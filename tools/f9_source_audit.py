#!/usr/bin/env python3
"""Read-only static inspection of the checked-out F9 Android source tree.

Do not confuse SOURCE_CHECK_ONLY with APK integrity, MASVS dynamic assessment,
physical acceptance, cryptographic signing or publication authorization.
"""
import argparse
import json
import pathlib
import sys
from f9_security_preflight import _analyze_manifest, _analyze_gradle, _analyze_ci, _issue


def run_source_audit(root, channel='internal-debug'):
    if channel not in {'internal-debug', 'public-release'}:
        raise ValueError('Unknown release channel')
    root=pathlib.Path(root)
    findings=[]
    _analyze_manifest(root/'app/src/main/AndroidManifest.xml', findings)
    _analyze_gradle(root/'app/build.gradle.kts', findings, channel)
    _analyze_ci(root/'.github/workflows/android.yml', findings, channel)
    if channel=='public-release':
        _issue(findings, 'PUBLIC_RELEASE_NOT_ATTESTED',
               'Source-only inspection cannot attest stable signature, APK, MASTG, real device or owner approval')
    decision=('BLOCKED' if any(f['level']=='BLOCKER' for f in findings)
              else 'SOURCE_CHECK_ONLY')
    return {'schema':'f9-appsec-source-audit-v1', 'channel':channel,
            'decision':decision, 'provenance_verified':False,
            'physical_certified':False, 'release_certified':False,
            'findings':findings,
            'limitations':['static source tree only, not merged Android binary manifest',
                           'APK signing, dynamic MASTG, physical testing and Play policies not verified']}


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=pathlib.Path,default=pathlib.Path('.'))
    p.add_argument('--channel',choices=['internal-debug','public-release'],default='internal-debug')
    p.add_argument('--report',type=pathlib.Path)
    args=p.parse_args(argv)
    result=run_source_audit(args.root,args.channel)
    value=json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+'\n'
    if args.report:
        args.report.write_text(value,encoding='utf8')
    else:
        print(value,end='')
    return 2 if result['decision']=='BLOCKED' else 0


if __name__=='__main__':
    sys.exit(main())
