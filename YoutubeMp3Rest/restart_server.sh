echo "KILLING OLD SERVER SESSION"
kill $(cat lastPID.txt)
sleep 3
echo "STARTING SERVER..."
# java -Dfile.encoding=utf-8 -jar youtube.to.mp3-0.0.1-SNAPSHOT-jar-with-dependencies.jar </dev/null>/dev/null 2>&1 &
java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9199 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=176.111.109.11 -jar youtube.to.mp3-0.0.1-SNAPSHOT-jar-with-dependencies.jar </dev/null>/dev/null 2>&1 &
echo $! > lastPID.txt
echo "SERVER STARTED! PID:" $(cat lastPID.txt)
