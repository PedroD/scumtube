echo "KILLING OLD SERVER SESSION"
kill $(cat lastPID.txt)
sleep 10
echo "STARTING SERVER..."
java -Dfile.encoding=utf-8 -jar youtube.to.mp3-0.0.1-SNAPSHOT-jar-with-dependencies.jar </dev/null>/dev/null 2>&1 &
echo $! > lastPID.txt
echo "SERVER STARTED! PID:" $(cat lastPID.txt)
