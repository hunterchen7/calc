#!/usr/bin/env python3
"""
Compare two emulator traces to find the first divergence point.

Usage:
    python compare_traces.py trace_cemu.log trace_ours.log

CEmu counts DD/FD prefixes as separate instructions, while our emulator
executes them together with the following opcode. This script handles
that difference by syncing on PC values.
"""

import sys
import re

def parse_cemu_line(line):
    """Parse a CEmu trace line and extract key fields."""
    if not line.startswith('[inst]'):
        return None

    match = re.search(r'i=(\d+)\s+PC=([0-9A-Fa-f]+)', line)
    if match:
        return {
            'i': int(match.group(1)),
            'pc': match.group(2).upper(),
            'raw': line.strip()
        }
    return None

def parse_ours_line(line):
    """Parse our trace line and extract key fields."""
    if not line.startswith('[snapshot]'):
        return None

    match = re.search(r'step=(\d+)\s+PC=([0-9A-Fa-f]+)', line)
    if match:
        return {
            'i': int(match.group(1)),
            'pc': match.group(2).upper(),
            'raw': line.strip()
        }
    return None

def compare_traces(cemu_file, ours_file, max_lines=500000):
    """Compare traces by syncing on PC values (handles prefix counting differences)."""

    print(f"Loading CEmu trace from {cemu_file}...")
    cemu_entries = []
    with open(cemu_file, 'r') as f:
        for i, line in enumerate(f):
            if i >= max_lines:
                break
            entry = parse_cemu_line(line)
            if entry:
                cemu_entries.append(entry)
    print(f"  Loaded {len(cemu_entries)} instructions from CEmu trace")

    print(f"Loading our trace from {ours_file}...")
    ours_entries = []
    with open(ours_file, 'r') as f:
        for i, line in enumerate(f):
            if i >= max_lines:
                break
            entry = parse_ours_line(line)
            if entry:
                ours_entries.append(entry)
    print(f"  Loaded {len(ours_entries)} instructions from our trace")

    # Compare by syncing on PC values
    # CEmu counts prefix bytes (DD/FD) as separate instructions, we don't
    print("\nComparing traces (syncing on PC)...")

    cemu_idx = 0
    ours_idx = 0
    matched_count = 0

    while ours_idx < len(ours_entries) and cemu_idx < len(cemu_entries):
        ours = ours_entries[ours_idx]
        cemu = cemu_entries[cemu_idx]

        if cemu['pc'] == ours['pc']:
            # PCs match, advance both
            matched_count += 1
            cemu_idx += 1
            ours_idx += 1
        else:
            # PCs differ - CEmu might be on a prefix byte
            # Try advancing CEmu up to 2 steps to sync (for nested prefixes)
            synced = False
            for lookahead in range(1, 3):
                if cemu_idx + lookahead < len(cemu_entries):
                    if cemu_entries[cemu_idx + lookahead]['pc'] == ours['pc']:
                        # Found sync point - CEmu was on prefix bytes
                        cemu_idx += lookahead
                        synced = True
                        break

            if synced:
                # Continue comparison from synced point
                continue

            # True divergence - couldn't sync
            print(f"\n*** DIVERGENCE FOUND ***")
            print(f"After {matched_count} matched PC values")
            print(f"\nCEmu (i={cemu['i']}, idx={cemu_idx}):")
            print(f"  PC={cemu['pc']}")
            print(f"  {cemu['raw'][:200]}...")
            print(f"\nOurs (step={ours['i']}, idx={ours_idx}):")
            print(f"  PC={ours['pc']}")
            print(f"  {ours['raw'][:200]}...")

            # Show context (last 5 matched PCs)
            print(f"\n--- Context (5 entries before in our trace) ---")
            for j in range(max(0, ours_idx-5), ours_idx):
                print(f"Ours[{j}]: PC={ours_entries[j]['pc']}")

            # Find the same PCs in CEmu for comparison
            print(f"\n--- CEmu entries around cemu_idx={cemu_idx} ---")
            for j in range(max(0, cemu_idx-5), min(cemu_idx+3, len(cemu_entries))):
                marker = " <-- divergence" if j == cemu_idx else ""
                print(f"CEmu[{j}]: PC={cemu_entries[j]['pc']}{marker}")

            return True

    print(f"\nNo divergence found!")
    print(f"Matched {matched_count} PC values")
    print(f"CEmu entries processed: {cemu_idx}")
    print(f"Our entries processed: {ours_idx}")

    if ours_idx < len(ours_entries):
        print(f"\nOur trace continues beyond CEmu:")
        print(f"  Next: PC={ours_entries[ours_idx]['pc']}")
    elif cemu_idx < len(cemu_entries):
        print(f"\nCEmu trace continues beyond ours:")
        print(f"  Next: PC={cemu_entries[cemu_idx]['pc']}")
    else:
        print(f"\nBoth traces ended at same point")
        print(f"Last PC: CEmu={cemu_entries[-1]['pc']}, Ours={ours_entries[-1]['pc']}")

    return False

def main():
    if len(sys.argv) < 3:
        print("Usage: python compare_traces.py <cemu_trace> <our_trace>")
        print("Example: python compare_traces.py trace_cemu.log trace_ours_new.log")
        sys.exit(1)

    cemu_file = sys.argv[1]
    ours_file = sys.argv[2]

    compare_traces(cemu_file, ours_file)

if __name__ == '__main__':
    main()
