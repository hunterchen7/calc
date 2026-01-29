//! Clean trace logger - every instruction, no ON key press, CEmu-compatible format
//!
//! Run: cargo run --release --example clean_trace > trace_clean.log 2>&1

use std::fs;
use std::path::Path;

use emu_core::Emu;

fn main() {
    let rom_paths = ["TI-84 CE.rom", "../TI-84 CE.rom"];

    let mut rom_data = None;
    for path in &rom_paths {
        if Path::new(path).exists() {
            if let Ok(data) = fs::read(path) {
                rom_data = Some(data);
                break;
            }
        }
    }

    let rom_data = match rom_data {
        Some(data) => data,
        None => {
            eprintln!("No ROM file found.");
            return;
        }
    };

    let mut emu = Emu::new();
    emu.load_rom(&rom_data).expect("Failed to load ROM");

    // Print timestamp header
    eprintln!("=== Our emulator trace ===");
    eprintln!(
        "Generated: {}",
        std::process::Command::new("date")
            .arg("+%Y-%m-%d %H:%M:%S")
            .output()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_else(|_| "unknown".to_string())
    );

    // Run 250,000 instructions to match CEmu trace length, no ON key press
    let max_instructions = 250_000u64;

    for i in 0..max_instructions {
        let pc = emu.pc();
        let sp = emu.sp();
        let adl = emu.adl();
        let iff1 = emu.iff1();
        let iff2 = emu.iff2();
        let halted = emu.is_halted();
        let im = emu.interrupt_mode();
        let intr_stat = emu.interrupt_status();
        let intr_en = emu.interrupt_enabled();
        let intr_raw = emu.interrupt_raw();

        // Register values for debugging
        let hl = emu.hl();
        let de = emu.de();
        let bc = emu.bc();
        let af = ((emu.a() as u16) << 8) | (emu.f() as u16);

        let pwr = emu.control_read(0x00);
        let spd = emu.control_read(0x01);
        let unlock = emu.control_read(0x06);
        let flash_unlock = emu.control_read(0x28);

        // Read opcode byte(s) - match CEmu format
        let op1 = emu.peek_byte(pc);
        let op2 = emu.peek_byte(pc.wrapping_add(1));
        let op3 = emu.peek_byte(pc.wrapping_add(2));
        let op4 = emu.peek_byte(pc.wrapping_add(3));

        let op_str = if op1 == 0xDD || op1 == 0xFD {
            if op2 == 0xCB {
                format!("{:02X} {:02X} {:02X} {:02X}", op1, op2, op3, op4)
            } else {
                format!("{:02X} {:02X}", op1, op2)
            }
        } else if op1 == 0xED || op1 == 0xCB {
            format!("{:02X} {:02X}", op1, op2)
        } else {
            format!("{:02X}", op1)
        };

        // Format like CEmu: [inst] i=N PC=...
        println!(
            "[snapshot] step={} PC={:06X} SP={:06X} AF={:04X} BC={:06X} DE={:06X} HL={:06X} IM={:?} ADL={} IFF1={} IFF2={} HALT={} INTR[stat={:06X} en={:06X} raw={:06X}] CTRL[pwr={:02X} spd={:02X} unlock={:02X} flash={:02X}] op={}",
            i, pc, sp, af, bc, de, hl, im, adl, iff1, iff2, halted,
            intr_stat & 0x3FFFFF, intr_en & 0x3FFFFF, intr_raw & 0x3FFFFF,
            pwr, spd, unlock, flash_unlock, op_str
        );

        if halted {
            eprintln!("HALTED at step {} PC={:06X}", i, pc);
            eprintln!("Interrupt status: {:06X}, enabled: {:06X}", intr_stat, intr_en);
            break;
        }

        emu.run_cycles(1);

        // Progress indicator
        if i > 0 && i % 50_000 == 0 {
            eprintln!("Progress: {} instructions...", i);
        }
    }

    eprintln!("Trace complete");
}
