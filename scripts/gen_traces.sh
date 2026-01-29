#!/bin/bash
# Generate timestamped trace files for both CEmu and our emulator
# Usage: ./scripts/gen_traces.sh

set -e

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
TRACES_DIR="traces"

# Create traces directory if it doesn't exist
mkdir -p "$TRACES_DIR"

CEMU_TRACE="$TRACES_DIR/cemu_${TIMESTAMP}.log"
OURS_TRACE="$TRACES_DIR/ours_${TIMESTAMP}.log"

echo "=== Generating traces with timestamp: $TIMESTAMP ==="
echo ""

# Generate CEmu trace
echo "Generating CEmu trace -> $CEMU_TRACE"
cd cemu-ref
./trace_cli > "../$CEMU_TRACE" 2>&1
cd ..
echo "  Done: $(wc -l < "$CEMU_TRACE") lines"

# Generate our trace
echo "Generating our trace -> $OURS_TRACE"
cd core
cargo run --release --example clean_trace > "../$OURS_TRACE" 2>&1
cd ..
echo "  Done: $(wc -l < "$OURS_TRACE") lines"

echo ""
echo "=== Comparing traces ==="
python3 scripts/compare_traces.py "$CEMU_TRACE" "$OURS_TRACE"

echo ""
echo "=== Trace files ==="
echo "CEmu: $CEMU_TRACE"
echo "Ours: $OURS_TRACE"
