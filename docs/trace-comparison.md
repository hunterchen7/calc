# CEmu vs emu-core Boot Trace Comparison

## Goal
Capture boot-time traces from both emulators, then compare to find divergences and fix them.

## How to capture traces
- emu-core: `cargo run --example trace_boot --manifest-path core/Cargo.toml > trace_ours.log`
- CEmu core (local clone in `cemu-ref`): `./cemu-ref/trace_cli > trace_cemu.log 2>&1`

Notes:
- emu-core trace logs CPU/interrupt/control state plus timers + LCD, and opcode bytes at PC.
- CEmu trace logs snapshot state changes plus per-instruction `[inst]` lines (PC + opcode bytes + key state) via a CPU trace callback.
- CEmu prints to stderr for some output; redirecting `2>&1` keeps the trace intact.

## How to compare traces
1. **Align the series.** emu-core logs `[snapshot]` once per step; CEmu logs `[inst]` once per instruction.
2. **Normalize differences.** CEmu uses `0/1` while emu-core logs `true/false`. Normalize booleans and `IM` formats before diffing.
3. **Compare a minimal field set first.** Start with `PC`, `SP`, `ADL`, `IFF1`, `IFF2`, `HALT`, and `op`.
4. **Find the earliest divergence** and focus fixes there; later diffs are usually cascading.

Quick alignment script (run from repo root):
```bash
python3 - <<'PY'
import re
from itertools import zip_longest

fields = ['PC','SP','ADL','IFF1','IFF2','HALT','op']

def norm_bool(v):
    if v in ('false','0'):
        return '0'
    if v in ('true','1'):
        return '1'
    return v

def norm_op(v):
    if v is None:
        return v
    return v.replace(' (init)','').strip()

def parse_line(line):
    d={}
    m=re.search(r'\\bop=([^\\]]+)$', line)
    if m:
        d['op']=norm_op(m.group(1).strip())
    for key in ['PC','SP','ADL','IFF1','IFF2','HALT']:
        m=re.search(r'\\b'+re.escape(key)+r'=?([^\\s\\]]+)', line)
        if m:
            val=m.group(1)
            if key in ('ADL','IFF1','IFF2','HALT'):
                val=norm_bool(val)
            d[key]=val
    return d

def parse(path, kind):
    out=[]
    with open(path,'r',errors='replace') as f:
        for line in f:
            line=line.rstrip('\\n')
            if line.startswith(kind):
                out.append(parse_line(line))
    return out

ours=parse('trace_ours.log','[snapshot]')
cemu=parse('trace_cemu.log','[inst]')

for i,(a,b) in enumerate(zip_longest(ours, cemu)):
    if a is None or b is None:
        print('length mismatch at', i, 'ours', len(ours), 'cemu', len(cemu))
        break
    diffs=[]
    for k in fields:
        if k in a and k in b and a[k]!=b[k]:
            diffs.append((k,a[k],b[k]))
    if diffs:
        print('diverge_at', i)
        print('diffs', diffs)
        print('ours', a)
        print('cemu', b)
        break
else:
    print('no divergence (on compared fields), len', len(ours))
PY
```

## Recommended approach
1. **Always fix the earliest divergence first.** Later diffs are usually cascading.
2. **Add trace-level assertions or micro-tests** for any fix (e.g., suffix/ADL semantics).
3. **Keep changes minimal and reversible.** Avoid broad tweaks until a trace shows a concrete mismatch.
4. **Use CEmu as reference, not guesswork.** If we can't explain a divergence using CEmu's decode/execute path, pause and inspect it.
