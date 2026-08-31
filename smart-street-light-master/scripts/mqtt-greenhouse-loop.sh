#!/bin/sh
# 光棚硬件路径模拟（适配 layout v1.4 密集灯/PAR）
# - 主路径：订阅 smart-greenhouse/+/command → 回 status（执行器 ACK）
# - 心跳：遮阳 + 抽样灯在线（不发 PAR telemetry，避免与 greenhouse.sim 进程内光场打架）
#
# 用法: docker compose --profile gh-hw-sim up -d
#   或 scripts/mqtt-simulate-greenhouse.ps1

INTERVAL="${INTERVAL_SEC:-5}"
BROKER="${BROKER_HOST:-127.0.0.1}"
PORT="${BROKER_PORT:-1883}"
ACK_MS="${ACK_DELAY_MS:-200}"

echo "[gh-hw-sim] start interval=${INTERVAL}s broker=${BROKER}:${PORT} (command-ACK + online heartbeat)"

(
  mosquitto_sub -h "$BROKER" -p "$PORT" -t 'smart-greenhouse/+/command' -v 2>/dev/null | while read -r TOPIC PAYLOAD; do
    SN=$(echo "$TOPIC" | awk -F/ '{print $2}')
    [ -z "$SN" ] && continue
    CMD=$(echo "$PAYLOAD" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    DIM=$(echo "$PAYLOAD" | sed -n 's/.*"dimmingPercent"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')
    SHADE=$(echo "$PAYLOAD" | sed -n 's/.*"shadeOpenPercent"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')
    TS=$(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S')
    if [ "$CMD" = "SET_DIMMING" ] && [ -n "$DIM" ]; then
      POW=false
      [ "$DIM" -gt 0 ] && POW=true
      STATUS="{\"deviceSn\":\"$SN\",\"deviceType\":\"GROW_LAMP\",\"online\":true,\"dimmingPercent\":$DIM,\"powerOn\":$POW,\"timestamp\":\"$TS\"}"
    elif [ "$CMD" = "SET_OPEN_PERCENT" ] && [ -n "$SHADE" ]; then
      STATUS="{\"deviceSn\":\"$SN\",\"deviceType\":\"SHADE_ACTUATOR\",\"online\":true,\"shadeOpenPercent\":$SHADE,\"motorState\":\"STOPPED\",\"timestamp\":\"$TS\"}"
    else
      STATUS="{\"deviceSn\":\"$SN\",\"online\":true,\"timestamp\":\"$TS\"}"
    fi
    usleep $((ACK_MS * 1000)) 2>/dev/null || sleep 0.2
    mosquitto_pub -h "$BROKER" -p "$PORT" -t "smart-greenhouse/$SN/status" -m "$STATUS" -q 0
    echo "[gh-hw-sim] ACK $SN $CMD"
  done
) &

# 心跳设备：两区遮阳 + 南/中(下层)/北 + L1
HEARTBEAT="
SHADE-ZONE-A
SHADE-ZONE-B
LAMP-ZONE-A-01
LAMP-ZONE-A-08
LAMP-ZONE-A-15
LAMP-ZONE-A-L1-03
LAMP-ZONE-A-L1-08
LAMP-ZONE-B-01
LAMP-ZONE-B-08
LAMP-ZONE-B-15
LAMP-ZONE-B-L1-03
"

while true; do
  TS=$(date -Iseconds 2>/dev/null || date '+%Y-%m-%dT%H:%M:%S')
  echo "$HEARTBEAT" | while read -r SN; do
    [ -z "$SN" ] && continue
    case "$SN" in
      SHADE-*)
        ST="{\"deviceSn\":\"$SN\",\"deviceType\":\"SHADE_ACTUATOR\",\"online\":true,\"motorState\":\"STOPPED\",\"timestamp\":\"$TS\"}"
        ;;
      *)
        ST="{\"deviceSn\":\"$SN\",\"deviceType\":\"GROW_LAMP\",\"online\":true,\"timestamp\":\"$TS\"}"
        ;;
    esac
    mosquitto_pub -h "$BROKER" -p "$PORT" -t "smart-greenhouse/$SN/status" -m "$ST" -q 0
  done
  echo "[gh-hw-sim] heartbeat ok"
  sleep "$INTERVAL"
done
