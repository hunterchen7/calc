# CEmu vs Our Implementation: Comprehensive Comparison Report

This document compares our TI-84 Plus CE emulator implementation with the CEmu reference implementation.

## Executive Summary

The comparison agents identified several critical issues that could cause boot problems:

### Boot-Critical Issues (Must Fix)

1. **Flash Unlock Detection is DISABLED** - The most likely cause of boot hang
2. **EI Delay Not Implemented** - Interrupts enable immediately instead of after one instruction
3. **Flash Controller Not Event-Based** - `flash_set_map()` never called on register writes
4. **Timer Match Interrupts Missing** - Only zero/overflow interrupts work
5. **Keypad Scan Scheduling Missing** - No actual scan timing
6. **Protected Memory Access Not Enforced** - Reads should return 0 for unprivileged code

---

## 1. CPU Implementation

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **EI Delay Missing** | CRITICAL | CEmu delays interrupt enable by one instruction cycle. Our code enables immediately. |
| **L/IL Context Flags Missing** | HIGH | CEmu tracks separate L/IL flags for data/instruction addressing. We only have ADL. |
| **Block Instructions Single-Step** | MEDIUM | CEmu's LDIR loops internally; ours executes once per step() call. |
| **LD A,I Uses Wrong Flag** | MEDIUM | We set PV from IFF2; CEmu uses IFF1. |
| **PREFIX/SUFFIX Tracking** | MEDIUM | CEmu validates prefix nesting; we don't. |

### Recommendations

```rust
// Add EI delay mechanism
pub ei_delay: Option<u32>,  // Cycle when to enable IFF1/IFF2

// In step() before instruction execution:
if let Some(delay) = self.ei_delay {
    if cycles >= delay {
        self.iff1 = true;
        self.iff2 = true;
        self.ei_delay = None;
    }
}

// In EI instruction:
7 => {
    self.ei_delay = Some(current_cycle + 1);
    4
}
```

---

## 2. Control Ports (0xE00000)

### Critical Issues

| Port | Issue | Description |
|------|-------|-------------|
| 0x00 | Mask Difference | CEmu uses 0x93, we use 0x83 |
| 0x01 | Mask Difference | CEmu uses 0x13, we use 0x03 |
| 0x03 | Static Value | CEmu computes from `asic.serFlash`; we return 0x00 |
| 0x05 | No Crash Detection | CEmu crashes if bit 6 cleared; we allow it |
| 0x06-0x28 | Correct | Flash unlock logic matches CEmu |
| 0x07/0x09/0x0A/0x0C | Battery State Machine | CEmu has complex battery state machine; we don't |
| 0x3D/0x3E | Protection Status | Not implemented |

### Port 0x28 Flash Unlock (Correct)

Both implementations use the same formula:
```
flashUnlocked = (flashUnlocked | 5) & value
```

### is_unprivileged() Difference

CEmu computes `PC + 1` with address mode transformation. We compare raw PC.

---

## 3. Bus and Memory

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **Flash Unlock Detection DISABLED** | CRITICAL | Detection code is commented out - boot cannot proceed |
| **Flash Wait States Static** | HIGH | Always 10 cycles; should be configurable |
| **Protected Memory Not Enforced** | HIGH | Should return 0 for unprivileged data reads |
| **RAM Boundary Not Checked** | MEDIUM | Accesses to 0xD65800+ should return random |

### Flash Unlock Sequence

Our 16-byte sequence:
```
F3 18 00 F3 ED 7E ED 56 ED 39 28 ED 38 28 CB 57
```

CEmu's 17-byte sequence (note double DI):
```
F3 18 00 F3 F3 ED 7E ED 56 ED 39 28 ED 38 28 CB 57
```

**Action Required:** Verify which sequence the ROM uses and enable detection.

---

## 4. Flash Controller (0xE10000)

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **flash_set_map() Never Called** | CRITICAL | Register writes don't recalculate mapping |
| **No Serial Flash Support** | CRITICAL | Modern TI-84 CE uses serial flash |
| **No Cache Implementation** | HIGH | Cache miss takes 195+ cycles; we always use 10 |
| **Command Processor Missing** | HIGH | No SPI commands supported |

### Missing Port 0x07

CEmu initializes `ports[0x07] = 0xFF`; we don't handle this register.

### Recommendations

Add callback on register writes:
```rust
pub fn write(&mut self, addr: u32, value: u8) {
    match addr {
        regs::ENABLE => {
            self.enable = value & 0x01;
            self.recalculate_mapping();  // Add this
        }
        // ... same for other registers
    }
}
```

---

## 5. Interrupt Controller (0xF00000)

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **Missing Sources** | MEDIUM | Missing RTC (12), USB (13), UART (16), SPI (18) |
| **No Latching Semantics** | MEDIUM | CEmu has separate latch register |
| **No Inversion Support** | LOW | CEmu supports active-low interrupts |

### PWR Interrupt (Correct)

Both implementations set PWR interrupt (bit 15) on reset.

---

## 6. Timers (0xF20000)

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **Match Interrupts Not Implemented** | HIGH | Only INT_ON_ZERO works; match1/match2 ignored |
| **No Event Scheduling** | HIGH | Simple tick instead of event-based |
| **OS Timer Missing** | MEDIUM | OSTIMER source defined but not implemented |
| **No Status Register** | MEDIUM | CEmu tracks match/overflow status separately |

---

## 7. Keypad (0xF50000)

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **No Scan Scheduling** | HIGH | ROM waits for scan events that never occur |
| **Status Bits Don't Progress** | HIGH | No bit 1 (data changed), bit 2 (any key), bit 3 (scan done) |
| **No Ghosting** | LOW | Not boot-critical |

---

## 8. LCD Controller (0xE30000)

### Critical Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **No Timing Phases** | MEDIUM | No hsync/vsync/porch simulation |
| **Missing Interrupt Statuses** | MEDIUM | Only VBLANK (bit 0); missing underrun, timing update |
| **No Palette** | MEDIUM | 256-entry RGB565 palette not implemented |
| **No Cursor** | LOW | 34 cursor registers missing |
| **No DMA** | LOW | Display output via DMA not implemented |

---

## Priority Fix Order

### Phase 1: Enable Boot (Must Fix First)

1. **Re-enable flash unlock detection** in bus.rs
2. **Verify flash unlock sequence** matches ROM (16 vs 17 bytes)
3. **Add flash_set_map() callback** on register writes
4. **Implement EI delay** in CPU

### Phase 2: Correct Timing

5. **Make flash wait states dynamic**
6. **Implement timer event scheduling**
7. **Add keypad scan events**

### Phase 3: Feature Completeness

8. Implement protected memory access restrictions
9. Add missing interrupt sources
10. Implement timer match interrupts
11. Add LCD timing phases

---

## Parity Milestones (Tracking)

These milestones convert the findings above into a concrete checklist for
approaching CEmu-level parity. Cross-checked with `docs/milestones.md` so we
don’t repeat baseline work already marked complete there (e.g., basic LCD,
timers, interrupt controller, control ports, flash controller stubs). The items
below focus on *parity gaps* identified in this report.

### P0: Boot Unblockers (Parity-Critical)

- [ ] **Re-enable flash unlock detection** in the bus and verify the exact ROM
      unlock sequence (16 vs 17 bytes). Update the matcher to the ROM’s real
      sequence.
- [ ] **EI delay**: enable IFF1/IFF2 only after the next instruction executes.
- [ ] **Flash controller map updates**: trigger `flash_set_map()` (or equivalent)
      when flash regs are written (enable/size/map/control).
- [ ] **ON key wake interrupt path**: if the ON key wakes the CPU while an
      interrupt is pending, ensure an interrupt is actually taken on the next
      step (avoid falling through to `RETI` without a pushed return address).
- [ ] **Protected memory access**: unprivileged data reads should return 0 where
      CEmu enforces protection.
- [ ] **Keypad scan scheduling**: add scan events + status bit progression so
      ROM loops that wait for scan completion can advance.

### P1: CPU + Bus Correctness

- [ ] **L/IL context flags**: track instruction vs. data addressing contexts
      separately (CEmu’s L/IL flags).
- [ ] **Block instructions**: implement internal looping for `LDIR`/`CPIR`-style
      ops instead of single-stepping.
- [ ] **LD A,I flag source**: set PV from IFF1 (not IFF2).
- [ ] **Prefix/suffix validation**: reject invalid prefix nesting like CEmu.
- [ ] **Dynamic flash wait states**: tie bus timing to flash controller settings.
- [ ] **RAM boundary behavior**: reads in 0xD65800+ return unmapped/random.

### P2: Peripherals Parity

- [ ] **Flash controller completeness**: serial flash support, command processor,
      cache/miss timing, and port 0x07 behavior.
- [ ] **Interrupt controller**: add missing sources (RTC/USB/UART/SPI), latch
      semantics, and inversion support.
- [ ] **Timers**: match interrupts (match1/match2), status register bits, OS timer
      source, and event-based scheduling.
- [ ] **Keypad**: status bits (data changed/any key/scan done) + ghosting.
- [ ] **Control ports**: register masks (0x00/0x01), port 0x03 value, port 0x05
      crash behavior, battery state machine (0x07/0x09/0x0A/0x0C), protection
      status (0x3D/0x3E), and `is_unprivileged()` address translation.

### P3: LCD + Display Pipeline

- [ ] **Timing phases**: hsync/vsync/porch simulation.
- [ ] **Interrupt statuses**: underrun/timing update bits.
- [ ] **Palette**: 256-entry RGB565 palette support.
- [ ] **Cursor**: implement the cursor register block.
- [ ] **DMA path**: emulate display DMA behavior.


## Files Summary

| Our File | CEmu Reference | Status |
|----------|---------------|--------|
| `cpu/` | `cpu.c` | EI delay needed |
| `peripherals/control.rs` | `control.c` | Battery state machine missing |
| `bus.rs` + `memory.rs` | `mem.c` | Flash unlock disabled |
| `peripherals/flash.rs` | `flash.c` | Serial flash/cache missing |
| `peripherals/interrupt.rs` | `interrupt.c` | Sources missing |
| `peripherals/timer.rs` | `timers.c` | Match interrupts missing |
| `peripherals/keypad.rs` | `keypad.c` | Scan scheduling missing |
| `peripherals/lcd.rs` | `lcd.c` | Timing/palette/cursor missing |

---

## Conclusion

The most likely cause of boot failure is the **disabled flash unlock sequence detection**. The ROM expects to unlock flash during boot, and without detection, bit 3 of port 0x28 never gets set, causing the boot to either loop or take a different (wrong) code path.

Secondary issues include incorrect EI timing, missing flash wait state recalculation, and lack of timer/keypad event scheduling.

**Immediate Action:** Re-enable flash unlock detection and verify the sequence matches the ROM.
