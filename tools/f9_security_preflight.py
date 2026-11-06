#!/usr/bin/env python3
"""Phase-9 *static* Android security and APK-provenance preflight.

This does NOT inspect binary manifests, validate APK cryptographic signatures,
perform MASVS dynamic tests, or certify release/physical functionality.
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
SHA40 = re.compile(r'^[0-9a-f]{40}$')
HASH64 = re.compile(r'^[0-9a-f]{64}$')


def _issue(findings, code, message, level='BLOCKER'):
    findings.append({'code': code, 'level': level, 'message': message})


def _analyze_manifest(path, findings):
    try:
        root = ET.parse(path).getroot()
        app = root.find('application')
        if root.tag != 'manifest' or app is None:
            raise ValueError('manifest or application node absent')
    except (OSError, ET.ParseError, ValueError) as exc:
        _issue(findings, 'MANIFEST_UNREADABLE', str(exc))
        return
    if app.get(ANDROID+'allowBackup') != 'false':
        _issue(findings, 'BACKUP_ENABLED', 'Manifest must explicitly disable application backup for this preflight')
    if app.get(ANDROID+'usesCleartextTraffic') != 'false':
        _issue(findings, 'CLEARTEXT_TRAFFIC', 'Cleartext traffic must explicitly be disabled')
    perms={n.get(ANDROID+'name') for n in root.findall('uses-permission')}
    if 'android.permission.RECORD_AUDIO' not in perms:
        _issue(findings, 'RECORD_AUDIO_PERMISSION_MISSING', 'RECORD_AUDIO not declared')
    microphone_services=[]
    for component in app:
        if component.tag not in {'service','activity','activity-alias','receiver','provider'}:
            continue
        name=component.get(ANDROID+'name','')
        exported=component.get(ANDROID+'exported')
        fgs=(component.get(ANDROID+'foregroundServiceType') or '').split('|')
        if 'microphone' in fgs:
            microphone_services.append(name)
            if component.tag != 'service':
                _issue(findings,'INVALID_MICROPHONE_FGS',name+' is not a service')
        if exported != 'true':
            continue
        is_launcher=(component.tag in {'activity','activity-alias'} and any(
            {'android.intent.action.MAIN'}<= {n.get(ANDROID+'name') for n in f.findall('action')}
            and 'android.intent.category.LAUNCHER' in
                {n.get(ANDROID+'name') for n in f.findall('category')}
            for f in component.findall('intent-filter')))
        permission=component.get(ANDROID+'permission')
        allowed_service=(component.tag=='service' and permission in {
            'android.permission.BIND_QUICK_SETTINGS_TILE',
            'android.permission.BIND_VOICE_INTERACTION',
        } and 'microphone' not in fgs)
        if not (is_launcher or allowed_service):
            _issue(findings,'UNPROTECTED_EXPORTED_COMPONENT',
                   f'{component.tag} {name} exported without an allowlisted Android binding permission')
    if microphone_services and 'android.permission.FOREGROUND_SERVICE_MICROPHONE' not in perms:
        _issue(findings,'MICROPHONE_FGS_PERMISSION_MISSING',
               'Foreground microphone service lacks FOREGROUND_SERVICE_MICROPHONE permission')
    if not microphone_services:
        _issue(findings,'MICROPHONE_FGS_NOT_FOUND',
               'No microphone foreground service declared; manually verify expected behavior', 'WARNING')


def _analyze_gradle(path, findings, channel):
    try:
        src=path.read_text(encoding='utf-8')
    except OSError as e:
        _issue(findings,'GRADLE_UNREADABLE',str(e)); return
    m=re.search(r'\btargetSdk\s*=\s*(\d+)\b',src)
    if m is None:
        _issue(findings,'TARGET_SDK_NOT_STATIC','Static targetSdk could not be parsed')
    elif channel=='public-release' and int(m.group(1))<36:
        _issue(findings,'PLAY_TARGET_API_TOO_OLD',
               '2026 Google Play smartphone new-app/update submissions require target API 36+')
    # Non-executable Gradle text check; not a replacement for an AGP resolved manifest.
    release=re.search(r'\brelease\s*\{([^{}]*)\}',src,re.DOTALL)
    if not release:
        _issue(findings,'RELEASE_BLOCK_NOT_STATIC','Unable to inspect flat release block')
        return
    for prop,code in [('isMinifyEnabled','RELEASE_MINIFICATION_DISABLED'),
                      ('isShrinkResources','RELEASE_RESOURCE_SHRINK_DISABLED')]:
        if not re.search(r'\b'+prop+r'\s*=\s*true\b',release.group(1)):
            _issue(findings,code,prop+' must be enabled in release block')


def _analyze_ci(path,findings,channel):
    try:
        src=path.read_text(encoding='utf-8').lower()
    except OSError as e:
        _issue(findings,'CI_WORKFLOW_UNREADABLE',str(e));return
    ephemeral='create ephemeral release signing key' in src
    if ephemeral:
        _issue(findings,'EPHEMERAL_CI_SIGNING',
               'CI keystore is temporary: cannot establish stable production signing identity',
               'BLOCKER' if channel=='public-release' else 'INFO')
    else:
        _issue(findings,'CI_SIGNING_NOT_ESTABLISHED',
               'No evidence that production signing identity is stable', 'WARNING')


def _parse_provenance(path, findings):
    try:
        lines=path.read_text(encoding='utf-8').splitlines()
    except OSError as e:
        _issue(findings,'PROVENANCE_MISSING',str(e))
        return {}
    p={}
    for line in lines:
        if '=' not in line:
            _issue(findings,'PROVENANCE_INVALID','Unexpected provenance line')
            return {}
        k,v=line.split('=',1)
        if k in p or k not in {'run_id','sha','apk_sha256'}:
            _issue(findings,'PROVENANCE_INVALID','Invalid/duplicate provenance key')
            return {}
        p[k]=v.strip()
    if set(p)!={'run_id','sha','apk_sha256'} or not p['run_id'].isdigit() or not SHA40.fullmatch(p['sha']) or not HASH64.fullmatch(p['apk_sha256']):
        _issue(findings,'PROVENANCE_INVALID','Invalid immutable provenance schema')
        return {}
    return p


def run_audit(root, apk, provenance_path, expected_sha, channel='internal-debug'):
    if channel not in {'internal-debug','public-release'}:
        raise ValueError('Unknown channel')
    root=pathlib.Path(root)
    apk=pathlib.Path(apk)
    prov=pathlib.Path(provenance_path)
    findings=[]
    verified=False
    _analyze_manifest(root/'app/src/main/AndroidManifest.xml',findings)
    _analyze_gradle(root/'app/build.gradle.kts',findings,channel)
    _analyze_ci(root/'.github/workflows/android.yml',findings,channel)
    if not SHA40.fullmatch(expected_sha or ''):
        _issue(findings,'EXPECTED_SHA_INVALID','Expected source SHA must be 40 lowercase hex')
    provenance=_parse_provenance(prov,findings)
    if provenance:
        if provenance['sha']!=expected_sha:
            _issue(findings,'PROVENANCE_SHA_MISMATCH','GitHub artifact provenance SHA differs from expected main SHA')
        try:
            with apk.open('rb') as handle:
                digest=hashlib.file_digest(handle,'sha256').hexdigest()
            if digest!=provenance['apk_sha256']:
                _issue(findings,'APK_HASH_MISMATCH','APK SHA-256 differs from GitHub provenance')
        except OSError as e:
            _issue(findings,'APK_UNREADABLE',str(e))
            digest=None
        verified=(digest is not None and digest==provenance['apk_sha256'] and provenance['sha']==expected_sha and SHA40.fullmatch(expected_sha or '') is not None)
    else:
        digest=None
    if channel=='public-release':
        _issue(findings,'PUBLIC_RELEASE_NOT_ATTESTED',
               'A debug artifact and static checks do not prove signature, Play upload key, physical tests, MASVS or approval')
    decision='BLOCKED' if any(f['level']=='BLOCKER' for f in findings) else 'STATIC_PRECHECK_ONLY'
    return {'schema':'f9-appsec-static-preflight-v1','channel':channel,'source_sha':expected_sha,
            'source_run_id':provenance.get('run_id'),'apk_sha256':digest,
            'provenance_verified':bool(verified),'physical_certified':False,
            'release_certified':False,'decision':decision,'findings':findings,
            'limitations':['source manifest/Gradle are text, not resolved binary manifest',
                           'does not verify cryptographic APK signature or Play upload key',
                           'does not run dynamic OWASP MASTG or tests on physical hardware']}


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=pathlib.Path,default=pathlib.Path('.'))
    p.add_argument('--apk',type=pathlib.Path,required=True)
    p.add_argument('--provenance',type=pathlib.Path,required=True)
    p.add_argument('--expected-sha',required=True)
    p.add_argument('--channel',choices=['internal-debug','public-release'],default='internal-debug')
    p.add_argument('--report',type=pathlib.Path)
    args=p.parse_args(argv)
    result=run_audit(args.root,args.apk,args.provenance,args.expected_sha,args.channel)
    payload=json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+'\n'
    if args.report:
        args.report.write_text(payload,encoding='utf-8')
    else:
        sys.stdout.write(payload)
    return 0 if result['decision']=='STATIC_PRECHECK_ONLY' else 2


if __name__=='__main__':
    sys.exit(main())
