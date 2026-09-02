#!/bin/sh
# Continuous MQTT fleet simulator for smart-street-light (runs inside mosquitto container netns)
# Publishes light + status for simulated devices to EMQX at 127.0.0.1:1883

INTERVAL="${INTERVAL_SEC:-8}"
BROKER="${BROKER_HOST:-127.0.0.1}"
PORT="${BROKER_PORT:-1883}"

# sn|bias|mode  mode=AUTO|MANUAL_ON|MISMATCH
DEVICES="
SN-RM-002|5|AUTO
SN-RM-003|12|AUTO
SN-JF-001|-3|MANUAL_ON
SN-JF-002|0|AUTO
SN-BJ-001|18|AUTO
SN-BJ-002|10|MISMATCH
SN-XQ-001|-15|AUTO
"

diurnal_lux() {
  # 平滑日变化，峰值约 380–420 lux（与室内真机同量级，避免中午突跳到 1000+）
  # $1=hour $2=minute $3=bias $4=seed
  h=$1; m=$2; bias=$3; seed=$4
  mins=$((h * 60 + m))
  awk -v mins="$mins" -v bias="$bias" -v seed="$seed" 'BEGIN{
    v = 8
    if (mins >= 300 && mins <= 1200) {
      x = (mins - 300) / 900
      v = 15 + 380 * (sin(3.14159265 * x) ^ 1.15)
    }
    noise = (seed * 17 + mins) % 11 - 5
    v = v + bias + noise
    if (v < 0.2) v = 0.2
    printf "%.2f", v
  }'
}

echo "[fleet-sim] start interval=${INTERVAL}s broker=${BROKER}:${PORT}"

while true; do
  H=$(date +%H)
  M=$(date +%M)
  # strip leading zeros for arithmetic
  H=$((10#$H))
  M=$((10#$M))
  TS=$(date '+%Y-%m-%d %H:%M:%S')
  tick=$((H * 60 + M))

  echo "$DEVICES" | while IFS='|' read -r SN BIAS MODE; do
    [ -z "$SN" ] && continue
    SEED=$(printf '%s' "$SN" | wc -c)
    LUX=$(diurnal_lux "$H" "$M" "$BIAS" "$SEED")
    # small live jitter ±3
    LUX=$(awk "BEGIN{j=(($tick+$SEED)%7)-3; v=$LUX+j; if(v<0.2)v=0.2; printf \"%.2f\", v}")

    case "$MODE" in
      MANUAL_ON) ST=ON ;;
      MISMATCH)  ST=ON ;; # keep ON while lux often high → C1 mismatch after AUTO OFF expected
      *)
        ST=OFF
        awk "BEGIN{exit !($LUX < 30)}" && ST=ON
        awk "BEGIN{exit !($LUX > 80)}" && ST=OFF
        # mid band: dawn/dusk hysteresis — prefer ON below 50
        if awk "BEGIN{exit !($LUX >= 30 && $LUX <= 80)}"; then
          if awk "BEGIN{exit !($LUX < 50)}"; then ST=ON; else ST=OFF; fi
        fi
        ;;
    esac

    LIGHT_JSON="{\"deviceSn\":\"$SN\",\"lightIntensity\":$LUX,\"timestamp\":\"$TS\"}"
    STATUS_JSON="{\"deviceSn\":\"$SN\",\"status\":\"$ST\",\"timestamp\":\"$TS\"}"

    mosquitto_pub -h "$BROKER" -p "$PORT" -t "smart-light/$SN/light" -m "$LIGHT_JSON" -q 0
    mosquitto_pub -h "$BROKER" -p "$PORT" -t "smart-light/$SN/status" -m "$STATUS_JSON" -q 0
    echo "[fleet-sim] $SN lux=$LUX status=$ST"
  done

  sleep "$INTERVAL"
done
