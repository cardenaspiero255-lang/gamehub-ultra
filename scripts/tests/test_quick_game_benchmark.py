import unittest
from unittest.mock import patch
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import quick_game_benchmark as q


def layer(name, pkg, fps=59.9, frames=600, histogram="16ms=200 17ms=389 34ms=9 54ms=1"):
    return (
        f"layerName = {name}\n"
        f"packageName = {pkg}\n"
        f"totalFrames = {frames}\n"
        f"averageFPS = {fps}\n"
        "present2present histogram is as below:\n"
        f"{histogram}\n"
    )


class ParseTests(unittest.TestCase):
    def test_select_game_surface_not_global_or_other_app(self):
        data = ("SurfaceFlinger TimeStats:\ntotalFrames = 1500\naverageFPS = 120\n"
                + layer('SurfaceView[com.real.game/.Main]#42', 'com.real.game')
                + layer('SurfaceView[com.other.game/.Main]#3', 'com.other.game', 120, 900))
        result = q.parse_surface_timestats(data, 'com.real.game')
        self.assertEqual(result['fps_presented'], 59.9)
        self.assertEqual(result['frame_count'], 600)
        self.assertEqual(result['layer'], 'SurfaceView[com.real.game/.Main]#42')
        self.assertAlmostEqual(result['slow_30fps_percent'], 10 / 599 * 100, places=2)
        self.assertAlmostEqual(result['p95_present_interval_ms'], 17)
        self.assertLess(result['p99_present_fps_estimate'], 59)

    def test_same_surface_split_into_refresh_buckets_merges_all_frames(self):
        name = 'SurfaceView[com.real.game/.Main]#42'
        data = (layer(name, 'com.real.game', 60, 18, '16ms=18')
                + layer(name, 'com.real.game', 30, 22, '33ms=22'))
        result = q.parse_surface_timestats(data, 'com.real.game')
        self.assertIsNotNone(result)
        self.assertEqual(result['frame_count'], 40)
        self.assertEqual(result['rate_bucket_count'], 2)
        self.assertAlmostEqual(result['fps_presented'], 38.71, places=2)
        self.assertEqual(result['p50_present_interval_ms'], 33)
        self.assertEqual(result['p95_present_interval_ms'], 33)
        self.assertAlmostEqual(result['slow_30fps_percent'], 0.0)
        self.assertEqual(q.parse_surface_timestats(data, 'com.real.game', layer_name=name), result)

    def test_multiple_real_surfaces_remain_ambiguous_with_rate_buckets(self):
        game = 'com.real.game'
        data = (layer('SurfaceView[com.real.game/Main]#1', game, 60, 20, '16ms=20')
                + layer('SurfaceView[com.real.game/Main]#1', game, 30, 20, '33ms=20')
                + layer('SurfaceView[com.real.game/Menu]#2', game, 90, 40, '11ms=40'))
        with self.assertRaises(q.AmbiguousLayerError):
            q.parse_surface_timestats(data, game)

    def test_incomplete_or_invalid_rate_bucket_does_not_exaggerate_fps(self):
        good = layer('SurfaceView[com.real.game/Main]#1', 'com.real.game', 60, 100, '16ms=100')
        unmeasured = layer('SurfaceView[com.real.game/Main]#1', 'com.real.game', 0, 100, '16ms=100')
        self.assertIsNone(q.parse_surface_timestats(good + unmeasured, 'com.real.game'))
        # If one bucket has no interval histogram, pacing must remain explicitly unknown.
        no_hist = layer('SurfaceView[com.real.game/Main]#1', 'com.real.game', 30, 100, '')
        result = q.parse_surface_timestats(good + no_hist, 'com.real.game')
        self.assertEqual(result['frame_count'], 200)
        self.assertAlmostEqual(result['fps_presented'], 40.0)
        self.assertIsNone(result['p95_present_interval_ms'])
        self.assertIsNone(result['slow_30fps_percent'])

    def test_bucket_merging_does_not_mix_different_process_uids(self):
        game = 'com.real.game'
        name = 'SurfaceView[com.real.game/Main]#1'
        def uid_bucket(uid):
            return layer(name,game,60,70,'16ms=69').replace('totalFrames', f'uid = {uid}\ntotalFrames')
        with self.assertRaises(q.AmbiguousLayerError):
            q.parse_surface_timestats(uid_bucket(10001)+uid_bucket(10002),game)

    def test_does_not_match_substring_package(self):
        data = layer('SurfaceView[com.real.game.beta/.Main]', 'com.real.game.beta')
        self.assertIsNone(q.parse_surface_timestats(data, 'com.real.game'))

    def test_ambiguous_game_layers_fail_closed(self):
        data = (layer('SurfaceView[com.real.game/Main]#1','com.real.game')
                + layer('SurfaceView[com.real.game/Other]#2','com.real.game',55))
        with self.assertRaisesRegex(q.AmbiguousLayerError, 'SurfaceView'):
            q.parse_surface_timestats(data,'com.real.game')
        result = q.parse_surface_timestats(data,'com.real.game',layer_name='SurfaceView[com.real.game/Other]#2')
        self.assertEqual(result['fps_presented'],55)

    def test_fallback_no_surface_means_no_verified_fps(self):
        data = layer('SurfaceView[com.real.game/Main]#1','com.real.game',fps=0,frames=0,histogram='')
        self.assertIsNone(q.parse_surface_timestats(data,'com.real.game'))
        self.assertIsNone(q.parse_surface_timestats('Permission Denial: SurfaceFlinger','com.real.game'))

    def test_no_valid_histogram_must_not_invent_pacing(self):
        data = layer('SurfaceView[com.real.game/Main]#1','com.real.game',fps=60,frames=300,histogram='')
        metrics = q.parse_surface_timestats(data,'com.real.game')
        self.assertEqual(metrics['fps_presented'],60)
        self.assertIsNone(metrics['p95_present_interval_ms'])
        self.assertIsNone(metrics['slow_30fps_percent'])

    def test_histogram_supports_modern_presentToPresent_spelling(self):
        data = layer('SurfaceView[com.real.game/Main]','com.real.game',60,120,'16ms=119')
        data = data.replace('present2present', 'presentToPresent')
        self.assertEqual(q.parse_surface_timestats(data,'com.real.game')['p95_present_interval_ms'],16)

    def test_reject_stale_histogram_unrelated_to_frames(self):
        data = layer('SurfaceView[com.real.game/Main]','com.real.game',60,500,'16ms=1 17ms=2')
        self.assertIsNone(q.parse_surface_timestats(data,'com.real.game')['slow_30fps_percent'])

    def test_battery_temperature_is_not_cpu_gpu_temperature(self):
        result = q.parse_battery('level: 66\n  temperature: 348\n status: 2\n')
        self.assertEqual(result['battery_percent'],66)
        self.assertEqual(result['battery_sensor_celsius'],34.8)
        self.assertEqual(result['battery_status_code'],2)
        self.assertIsNone(q.parse_battery('temperature: -500\n')['battery_sensor_celsius'])

    def test_thermal_status_does_not_claim_celsius(self):
        self.assertEqual(q.parse_thermal('Thermal Status: 3'), 3)
        self.assertEqual(q.parse_thermal('mStatus=1'), 1)
        self.assertIsNone(q.parse_thermal('Thermal data unavailable'))

    def test_ping_parser_keeps_diagnostic_not_game_ping(self):
        out='2 packets transmitted, 2 received\nrtt min/avg/max/mdev = 9.1/12.3/16.2/3.5 ms'
        self.assertEqual(q.parse_diagnostic_ping(out),12.3)
        self.assertIsNone(q.parse_diagnostic_ping('Network unreachable'))

    def test_detects_only_foreground_android_activity(self):
        log = ("topResumedActivity=ActivityRecord{a1 u0 com.real.game/.Main t22}\n"
               "mResumedActivity: ActivityRecord{a2 u0 com.old.game/.Main t20}\n")
        self.assertEqual(q.parse_foreground_package(log), 'com.real.game')
        self.assertIsNone(q.parse_foreground_package('no resumed activity'))
        self.assertIsNone(q.parse_foreground_package(
            'topResumedActivity=ActivityRecord{a1 u0 com.android.systemui/.Main t22}'
        ))

    def test_duration_is_bounded_and_package_validated(self):
        self.assertEqual(q.duration_type('15'),15)
        for value in ('0','180','-1','NaN'):
            with self.assertRaises(Exception): q.duration_type(value)
        for pkg in ('com.valid.game;rm -rf /', 'com.x $(echo wow)', 'bad', ''):
            with self.assertRaises(Exception): q.validate_package(pkg)


class RunnerTests(unittest.TestCase):
    def test_disable_collection_after_dump_failure(self):
        commands=[]
        def fake(args, timeout=5):
            commands.append(args)
            if args[-1]=='-dump':
                raise q.AdbError('device unplugged')
            if args[-1]=='devices':
                return 'List of devices attached\nABC\tdevice\n'
            if args[-1]=='com.real.game' and args[-2]=='pidof':
                return '42'
            return ''
        with patch.object(q,'adb',side_effect=fake), patch.object(q.time,'sleep',return_value=None):
            with self.assertRaises(q.AdbError):
                q.collect('com.real.game',seconds=15,ping=False)
        self.assertTrue(any(cmd[-1]=='-disable' for cmd in commands))

    def test_missing_physical_device_does_not_sleep_or_return_fake_numbers(self):
        with patch.object(q,'adb',return_value='List of devices attached\n'):
            with patch.object(q.time,'sleep') as sleeper:
                with self.assertRaisesRegex(q.AdbError,'Conecta'):
                    q.collect('com.real.game',seconds=15,ping=False)
                sleeper.assert_not_called()

    def test_auto_detects_foreground_package_without_user_input(self):
        calls=[]
        def fake(args, timeout=5):
            calls.append(args)
            tail=' '.join(args)
            if tail.endswith('devices'): return 'List of devices attached\nABC\tdevice\n'
            if tail.endswith('dumpsys activity activities'):
                return 'topResumedActivity=ActivityRecord{a1 u0 com.real.game/.Main t22}'
            if tail.endswith('pidof com.real.game'): return '123'
            if tail.endswith('dumpsys battery'): return 'level: 70\ntemperature: 330'
            if tail.endswith('dumpsys thermalservice'): return 'Thermal Status: 1'
            if tail.endswith('-dump'):return layer('SurfaceView[com.real.game/.Main]','com.real.game',59.9,600)
            return ''
        with patch.object(q,'adb',side_effect=fake), patch.object(q.time,'sleep',return_value=None):
            result=q.collect(None,seconds=15,ping=False)
        self.assertEqual(result['package'],'com.real.game')
        self.assertEqual(result['fps_presented'],59.9)

    def test_no_fps_data_reports_unavailable_but_battery_valid(self):
        calls=[]
        def fake(args, timeout=5):
            calls.append(args)
            tail=' '.join(args)
            if tail.endswith('devices'):
                return 'List of devices attached\nABC\tdevice\n'
            if tail.endswith('pidof com.real.game'): return '123'
            if tail.endswith('dumpsys battery'):return 'level: 75\ntemperature: 330\n'
            if tail.endswith('dumpsys thermalservice'):return 'Thermal Status: 1'
            if tail.endswith('-dump'):return 'Permission Denial: SurfaceFlinger'
            return ''
        with patch.object(q,'adb',side_effect=fake), patch.object(q.time,'sleep',return_value=None):
            data=q.collect('com.real.game',seconds=15,ping=False)
        self.assertIsNone(data['fps_presented'])
        self.assertEqual(data['measurement_status'],'unavailable')
        self.assertEqual(data['battery_end']['battery_percent'],75)
        self.assertIn('no comprobables',data['disclaimer'])


if __name__=='__main__': unittest.main()
