#!/usr/bin/env bash
# Writer regression: runs the reference jobs with Drive, one after another
# (they all write the file "foo"), and checks the sizes.
#
#   bash test/run_jobs.sh <image> [out_dir] [job...]
#
# Compiles the repo and Drive for Java 8 into <out_dir>/classes, then writes
# <out_dir>/<job>.bin and <job>.log for each job (all of them if none are
# named). For coffee.png (skimage.data.coffee()) the sizes are checked against
# EXPECTED below; for other images they are only printed. Exit status 1 if a
# job failed or a size differs.
#
# Needs a JDK (8 or later). Without a display it runs under xvfb-run.
# To check the files against the Python coders and the decoded image's md5,
# run DeltaEncoder3/test/parity.py on <out_dir> afterwards.

set -u
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${1:?usage: run_jobs.sh <image> [out_dir] [job...]}"
OUT="${2:-jobs_out}"
shift; [ $# -gt 0 ] && shift
IMAGE="$(cd "$(dirname "$IMAGE")" && pwd)/$(basename "$IMAGE")"
WAIT_S=5

# name | writer | fields
JOBS="
b     BlockWriter
d13   DeltaWriter   delta_type=13 block_set=3
d13c  DeltaWriter   delta_type=13 block_set=1 entropy_type=4
d2    DeltaWriter   delta_type=2
d2_13 DeltaWriter2  delta_type=13 block_set=3
p13   PacketWriter  delta_type=13 block_set=3
s13   SimpleWriter  delta_type=13 block_set=3 entropy_type=4
s13a  SimpleWriter  delta_type=13 block_set=0 entropy_type=2
"

# Sizes in bytes for coffee.png, with LS (predictor id 255).
declare -A EXPECTED=(
	[b]=89247 [d13]=101019 [d13c]=94427 [d2]=110560
	[d2_13]=101019 [p13]=101910 [s13]=93715 [s13a]=104759
)

mkdir -p "$OUT/classes" || exit 1
OUT="$(cd "$OUT" && pwd)"
echo "Compiling into $OUT/classes"
javac --release 8 -encoding UTF-8 -nowarn -Xlint:-options -d "$OUT/classes" "$REPO"/*.java "$REPO"/test/Drive.java || exit 1

if [ -n "${DISPLAY:-}" ]; then RUN=(); else RUN=(xvfb-run -a); fi
check=0; [ "$(basename "$IMAGE")" = coffee.png ] && check=1
failed=0
cd "$OUT" || exit 1

while read -r name writer fields; do
	[ -z "$name" ] && continue
	if [ $# -gt 0 ] && [[ ! " $* " =~ " $name " ]]; then continue; fi
	# shellcheck disable=SC2086
	"${RUN[@]}" java -cp "$OUT/classes" Drive "$writer" "$IMAGE" "$name.bin" "$WAIT_S" $fields > "$name.log" 2>&1
	size=$(sed -n 's/^DRIVE .* \([0-9]*\)$/\1/p' "$name.log")
	if [ -z "$size" ]; then
		printf '%-6s FAILED (see %s/%s.log)\n' "$name" "$OUT" "$name"; failed=1
	elif [ $check = 1 ] && [ "$size" != "${EXPECTED[$name]}" ]; then
		printf '%-6s %8s bytes  DIFFERS: expected %s\n' "$name" "$size" "${EXPECTED[$name]}"; failed=1
	elif [ $check = 1 ]; then
		printf '%-6s %8s bytes  ok\n' "$name" "$size"
	else
		printf '%-6s %8s bytes\n' "$name" "$size"
	fi
done <<< "$JOBS"

exit $failed
