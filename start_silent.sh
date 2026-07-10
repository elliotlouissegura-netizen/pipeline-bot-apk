#!/data/data/com.termux/files/usr/bin/bash
# Lancé par l'APK Pipeline Bot via Termux RUN_COMMAND
# Démarrage silencieux : pas de terminal visible

cd /data/data/com.termux/files/home
LOG="$HOME/bot.log"

# Déjà en cours ? On sort
if pgrep -f "dashboard.py" > /dev/null 2>&1; then
    echo "$(date): Bot déjà actif" >> "$LOG"
    exit 0
fi

echo "=== Démarrage $(date) ===" >> "$LOG"

# Scan initial
/data/data/com.termux/files/usr/bin/python main.py >> "$LOG" 2>&1

# Dashboard en arrière-plan
nohup /data/data/com.termux/files/usr/bin/python dashboard.py >> "$LOG" 2>&1 &
echo "Dashboard PID: $!" >> "$LOG"
