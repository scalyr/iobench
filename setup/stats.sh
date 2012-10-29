# usage example in your script
# . stats.sh
# stats_around mke2fs -j /dev/md0
# invokes mke2fs and also reports IO statistics before and after

function statcmd() {
	echo "cat /proc/diskstats"
}
function statcmd_disabled() {
	if [ -x "`which iostat 2>/dev/null`" ]; then
		echo "iostat -d -x"
	else
		echo "cat /proc/diskstats"
	fi
}
stat=`statcmd`
function stats() {
	date --iso-8601=seconds
	uptime
	$stat
}
function stats_around() {
	echo stats before "$@"
	stats
	"$@"
	result=$?
	echo stats after "$@"
	stats
	return $result
}
