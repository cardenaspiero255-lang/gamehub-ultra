#!/usr/bin/env python3
"""Short, fail-closed physical Android gaming benchmark via official SurfaceFlinger timestats.

Requires Python 3.9+ and adb, connected to an authorized Android device. Never
calculates FPS from display Hz or substitutes the global SurfaceFlinger FPS.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import time
from typing import Any


class AdbError(RuntimeError):
    """ADB or Android diagnostic failure."""


class AmbiguousLayerError(ValueError):
    """More than one surface plausibly belongs to the requested application."""


def duration_type(value: str) -> int:
    try:
        number = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("Usa un número entero de segundos") from exc
    if not 5 <= number <= 45:
        raise argparse.ArgumentTypeError("Duración permitida: de 5 a 45 segundos")
    return number


def validate_package(package: str) -> str:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+", package):
        raise ValueError("Paquete Android inválido. Ejemplo: com.example.juego")
    return package


def _field(section: str, key: str) -> str | None:
    found = re.search(rf"^{re.escape(key)}\s*=\s*(.*?)\s*$", section, re.MULTILINE)
    return found.group(1).strip() if found else None


def _weighted_percentile(buckets: dict[int, int], percentile: int) -> int | None:
    total = sum(buckets.values())
    if not total:
        return None
    threshold = math.ceil(total * percentile / 100)
    so_far = 0
    for millis, amount in sorted(buckets.items()):
        so_far += amount
        if so_far >= threshold:
            return millis
    return None


def _parse_histogram(section: str, frames: int) -> dict[str, float | None]:
    match = re.search(
        r"^present(?:2|to)present histogram is as below:\s*\n([^\n]+)",
        section, re.IGNORECASE | re.MULTILINE,
    )
    unknown = {"p50_present_interval_ms": None, "p95_present_interval_ms": None,
               "p99_present_interval_ms": None, "p99_present_fps_estimate": None,
               "slow_30fps_percent": None, "slow_20fps_percent": None,
               "present_interval_count": 0}
    if not match:
        return unknown
    buckets = {int(ms): int(count) for ms, count in re.findall(r"(\d+)ms=(\d+)", match.group(1))}
    buckets = {ms: count for ms, count in buckets.items() if ms > 0 and count > 0}
    count = sum(buckets.values())
    # A truncated/inconsistent histogram is not adequate evidence for pacing.
    if count < 10 or count < frames * 0.5 or count > frames:
        return unknown
    p50, p95, p99 = (_weighted_percentile(buckets, p) for p in (50, 95, 99))
    return {
        "p50_present_interval_ms": p50,
        "p95_present_interval_ms": p95,
        "p99_present_interval_ms": p99,
        "p99_present_fps_estimate": round(1000.0 / p99, 2) if p99 else None,
        "slow_30fps_percent": round(sum(v for ms, v in buckets.items() if ms >= 34) / count * 100, 2),
        "slow_20fps_percent": round(sum(v for ms, v in buckets.items() if ms > 50) / count * 100, 2),
        "present_interval_count": count,
    }


def _raw_present_histogram(section: str) -> dict[int, int] | None:
    match = re.search(
        r"^present(?:2|to)present histogram is as below:\s*\n([^\n]+)",
        section, re.IGNORECASE | re.MULTILINE,
    )
    if match is None:
        return None
    buckets: dict[int, int] = {}
    for ms, count in re.findall(r"(\d+)ms=(\d+)", match.group(1)):
        millis, amount = int(ms), int(count)
        if millis > 0 and amount > 0:
            buckets[millis] = buckets.get(millis, 0) + amount
    return buckets or None


def parse_surface_timestats(text: str, package: str, layer_name: str | None = None) -> dict[str, Any] | None:
    """Aggregate frame-rate buckets of ONE game surface, rejecting unrelated layers.

    AOSP TimeStats may emit the same layerName/uid in several render-rate and
    display-refresh buckets. Those buckets must be merged before checking the
    minimum frame count or determining if there are multiple *distinct* layers.
    """
    validate_package(package)
    grouped: dict[tuple[str, str | None], list[tuple[int, float | None, str]]] = {}
    # Modern AOSP writes uid before layerName, while older dumps can place uid
    # inside the layer block. A split at layerName must not steal the NEXT uid.
    sections = re.split(r"(?=^layerName\s*=)", text, flags=re.MULTILINE)
    for index, section in enumerate(sections[1:], 1):
        name = _field(section, "layerName")
        if not name:
            continue
        declared_package = _field(section, "packageName")
        exact_name_match = re.search(rf"(?<![A-Za-z0-9_.]){re.escape(package)}(?![A-Za-z0-9_.])", name)
        if declared_package != package and not (declared_package is None and exact_name_match):
            continue
        if layer_name is not None and name != layer_name:
            continue
        raw_frames = _field(section, "totalFrames")
        try:
            frames = int(raw_frames) if raw_frames is not None else 0
        except ValueError:
            frames = -1
        # Zero frames contain no sample. Invalid totals invalidate a candidate.
        if frames == 0:
            continue
        try:
            fps = float(_field(section, "averageFPS") or "nan")
        except ValueError:
            fps = float("nan")
        valid_fps = fps if math.isfinite(fps) and 1 <= fps <= 360 else None
        before_layer = sections[index - 1]
        prior_uid = re.search(r"(?:^|\n)uid\s*=\s*(\d+)\s*\n$", before_layer)
        header = section.split("totalFrames", 1)[0]
        uid = prior_uid.group(1) if prior_uid else _field(header, "uid")
        grouped.setdefault((name, uid), []).append((frames, valid_fps, section))

    if not grouped:
        return None
    if layer_name is None:
        preferred = {k: v for k, v in grouped.items() if "SurfaceView" in k[0]}
        if preferred:
            grouped = preferred
    if len(grouped) > 1:
        names = [k[0] + (f" (uid {k[1]})" if k[1] else "") for k in grouped]
        raise AmbiguousLayerError(
            "Varias superficies del juego encontradas: " + "; ".join(names) +
            ". Selecciona la exacta con --layer."
        )

    (name, _uid), entries = next(iter(grouped.items()))
    if any(frames < 0 or fps is None for frames, fps, _ in entries):
        return None  # Partial refresh-rate buckets cannot produce trustworthy FPS.
    frame_count = sum(frames for frames, _, _ in entries)
    if frame_count < 30:
        return None
    # FPS is the reciprocal of the frame-weighted average presentation interval.
    # An arithmetic mean of bucket FPS overstates adaptive refresh sessions.
    weighted_seconds = sum(frames / fps for frames, fps, _ in entries if fps is not None)
    if weighted_seconds <= 0:
        return None
    fps = round(frame_count / weighted_seconds, 2)

    merged: dict[int, int] = {}
    complete_histogram = True
    for frames, _, section in entries:
        part = _raw_present_histogram(section)
        if part is None or sum(part.values()) > frames:
            complete_histogram = False
            break
        for milliseconds, count in part.items():
            merged[milliseconds] = merged.get(milliseconds, 0) + count

    hist_section = ""
    if complete_histogram:
        hist_section = ("present2present histogram is as below:\n" +
                        " ".join(f"{ms}ms={count}" for ms, count in merged.items()) + "\n")
    return {
        "measurement_source": "SurfaceFlinger timestats / game-owned presented layer",
        "layer": name,
        "fps_presented": fps,
        "frame_count": frame_count,
        "rate_bucket_count": len(entries),
        **_parse_histogram(hist_section, frame_count),
    }


def parse_foreground_package(text: str) -> str | None:
    """Extract only Android's top/resumed activity, not arbitrary background packages."""
    for label in ("topResumedActivity", "mResumedActivity"):
        match = re.search(
            rf"^\s*{label}\s*[:=].*?\bu\d+\s+([A-Za-z0-9_.]+)/",
            text, re.MULTILINE,
        )
        if not match:
            continue
        candidate = match.group(1)
        if candidate in {"com.android.systemui", "com.android.launcher3"}:
            return None
        try:
            return validate_package(candidate)
        except ValueError:
            return None
    return None


def parse_battery(text: str) -> dict[str, int | float | None]:
    def num(key: str) -> int | None:
        found = re.search(rf"^\s*{key}:\s*(-?\d+)\b", text, re.MULTILINE)
        return int(found.group(1)) if found else None
    percent, temp, status = num("level"), num("temperature"), num("status")
    return {
        "battery_percent": percent if percent is not None and 0 <= percent <= 100 else None,
        # Android battery temperature is in tenths of degrees C. Not CPU/GPU temperature.
        "battery_sensor_celsius": round(temp / 10, 1) if temp is not None and -100 <= temp <= 900 else None,
        "battery_status_code": status if status is not None and 1 <= status <= 5 else None,
    }


def parse_thermal(text: str) -> int | None:
    match = re.search(
        r"\b(?:Thermal Status|mStatus|Current Thermal Status)\s*[:=]\s*(\d+)\b", text, re.IGNORECASE
    )
    value = int(match.group(1)) if match else -1
    return value if 0 <= value <= 6 else None


def parse_diagnostic_ping(text: str) -> float | None:
    match = re.search(r"(?:rtt|round-trip)\s+min/avg/max/(?:mdev|stddev)\s*=\s*([\d.]+)/([\d.]+)/", text)
    if not match:
        return None
    avg = float(match.group(2))
    return round(avg, 2) if math.isfinite(avg) and 0 <= avg <= 10_000 else None


def adb(args: list[str], timeout: float = 5) -> str:
    try:
        proc = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=timeout, check=False)
    except FileNotFoundError as exc:
        raise AdbError("ADB no está instalado o no está en PATH. Instala Android Platform Tools.") from exc
    except subprocess.TimeoutExpired as exc:
        raise AdbError("Tiempo de espera agotado ejecutando ADB.") from exc
    if proc.returncode:
        raise AdbError((proc.stderr or proc.stdout).strip()[:400] or "Comando ADB falló")
    return proc.stdout


def collect(package: str | None, seconds: int = 15, ping: bool = True, serial: str | None = None,
            layer: str | None = None) -> dict[str, Any]:
    if package is not None:
        validate_package(package)
    if not 5 <= seconds <= 45:
        raise ValueError("Duración fuera de rango")
    devices = re.findall(r"^([^\s]+)\s+device\s*$", adb(["adb", "devices"]), re.MULTILINE)
    if serial:
        if serial not in devices:
            raise AdbError("El dispositivo ADB solicitado no está autorizado/conectado.")
        device = serial
    else:
        if len(devices) != 1:
            raise AdbError("Conecta exactamente un teléfono con depuración USB autorizada (adb devices).")
        device = devices[0]
    prefix = ["adb", "-s", device, "shell"]
    def shell(*cmd: str, timeout: float = 5) -> str:
        return adb(prefix + list(cmd), timeout=timeout)

    if package is None:
        package = parse_foreground_package(shell("dumpsys", "activity", "activities"))
        if package is None:
            raise AdbError("No pude identificar la app en primer plano. Usa --package com.tu.juego.")
    if not shell("pidof", package).strip():
        raise AdbError(f"Abre el juego {package} antes de ejecutar la medición.")

    def optional(*command: str) -> str:
        try:
            return shell(*command)
        except AdbError:
            return ""

    battery_start = parse_battery(optional("dumpsys", "battery"))
    thermal_start = parse_thermal(optional("dumpsys", "thermalservice"))
    start_output = shell("dumpsys", "SurfaceFlinger", "--timestats", "-clear", "-enable")
    if re.search(r"(permission\s+denied|permission\s+denial|unknown\s+option|not\s+supported)",
                 start_output, re.IGNORECASE):
        raise AdbError("SurfaceFlinger TimeStats no está disponible en este dispositivo.")

    try:
        # The measurement window is intentionally short; do not run Gradle here.
        time.sleep(seconds)
        dump = shell("dumpsys", "SurfaceFlinger", "--timestats", "-dump", timeout=10)
    finally:
        # Always restore the global TimeStats sampling switch even on failures.
        try:
            shell("dumpsys", "SurfaceFlinger", "--timestats", "-disable")
        except AdbError:
            pass

    battery_end = parse_battery(optional("dumpsys", "battery"))
    thermal_end = parse_thermal(optional("dumpsys", "thermalservice"))
    ping_avg = None
    if ping:
        ping_avg = parse_diagnostic_ping(optional("ping", "-c", "2", "-W", "1", "1.1.1.1"))

    try:
        frame_data = parse_surface_timestats(dump, package, layer)
        error = None if frame_data else "No hay una superficie del juego inequívoca con al menos 30 fotogramas."
    except AmbiguousLayerError as exc:
        frame_data, error = None, str(exc)
    result: dict[str, Any] = {
        "package": package,
        "requested_duration_seconds": seconds,
        "measurement_status": "measured" if frame_data else "unavailable",
        "fps_presented": frame_data["fps_presented"] if frame_data else None,
        "frame_count": frame_data["frame_count"] if frame_data else None,
        "p95_present_interval_ms": frame_data["p95_present_interval_ms"] if frame_data else None,
        "p99_present_interval_ms": frame_data["p99_present_interval_ms"] if frame_data else None,
        "slow_30fps_percent": frame_data["slow_30fps_percent"] if frame_data else None,
        "slow_20fps_percent": frame_data["slow_20fps_percent"] if frame_data else None,
        "surface_layer": frame_data["layer"] if frame_data else None,
        "fps_unavailable_reason": error,
        "battery_start": battery_start,
        "battery_end": battery_end,
        "android_thermal_status_start": thermal_start,
        "android_thermal_status_end": thermal_end,
        "public_diagnostic_ping_ms": ping_avg,
        "disclaimer": (
            "FPS corresponde a presentaciones de la superficie del juego, no a Hz de pantalla; "
            "jank/p95 son aproximaciones por intervalos de histograma. "
            "Ping público no equivale a latencia del servidor del juego. "
            "Temperatura del sensor de batería no equivale a CPU/GPU. "
            "Métricas ausentes permanecen no comprobables."
        ),
    }
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Benchmark ADB rápido (15 s) para Android; no requiere instalar APK.")
    parser.add_argument("--package", help="Paquete del juego; se detecta automáticamente si se omite")
    parser.add_argument("--seconds", type=duration_type, default=15, help="5–45 s (predeterminado: 15)")
    parser.add_argument("--serial", help="Serial de adb devices, si hay varios teléfonos")
    parser.add_argument("--layer", help="Nombre exacto de SurfaceFlinger, si hay varias capas")
    parser.add_argument("--no-ping", action="store_true", help="Omitir prueba pública opcional de red")
    parser.add_argument("--output", default="quick-game-benchmark.json", help="Archivo JSON local")
    args = parser.parse_args(argv)
    try:
        result = collect(args.package, args.seconds, not args.no_ping, args.serial, args.layer)
    except (AdbError, ValueError) as exc:
        print(f"Benchmark no ejecutado: {exc}", file=sys.stderr)
        return 2
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Juego: {result['package']} | Captura: {args.seconds} s")
    print("FPS presentados:", result["fps_presented"] if result["fps_presented"] is not None else "No verificables")
    print("P95 intervalo de presentación:", result["p95_present_interval_ms"] if result["p95_present_interval_ms"] is not None else "No medido")
    print("Fotogramas lentos (<30 FPS):", result["slow_30fps_percent"] if result["slow_30fps_percent"] is not None else "No medidos")
    print("Sensor de batería al terminar:", result["battery_end"]["battery_sensor_celsius"])
    print("Estado térmico Android al terminar:", result["android_thermal_status_end"])
    print("Ping 1.1.1.1 (NO es servidor del juego):", result["public_diagnostic_ping_ms"])
    if result["fps_unavailable_reason"]:
        print("Motivo:", result["fps_unavailable_reason"])
    print("Informe:", output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
