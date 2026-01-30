//! Debug tool for testing keypad functionality
//!
//! Run with: cargo run --example keypad_debug

use std::io::{self, BufRead, Write};

fn main() {
    println!("Keypad Debug Tool");
    println!("=================");
    println!("This tests the keypad pipeline without Android.");
    println!();

    // Create emulator
    let emu = unsafe { emu_core::emu_create() };
    if emu.is_null() {
        eprintln!("Failed to create emulator!");
        return;
    }
    println!("✓ Emulator created");

    // Set up log callback
    unsafe {
        emu_core::emu_set_log_callback(Some(log_callback));
    }

    println!();
    println!("Commands:");
    println!("  p <row> <col>  - Press key at (row, col)");
    println!("  r <row> <col>  - Release key at (row, col)");
    println!("  d              - Dump all keypad data registers");
    println!("  s              - Show keypad status/control registers");
    println!("  c <cycles>     - Run emulation for N cycles");
    println!("  q              - Quit");
    println!();

    let stdin = io::stdin();
    let mut stdout = io::stdout();

    loop {
        print!("> ");
        stdout.flush().unwrap();

        let mut line = String::new();
        if stdin.lock().read_line(&mut line).is_err() {
            break;
        }

        let parts: Vec<&str> = line.trim().split_whitespace().collect();
        if parts.is_empty() {
            continue;
        }

        match parts[0] {
            "p" | "press" => {
                if parts.len() >= 3 {
                    let row: i32 = parts[1].parse().unwrap_or(-1);
                    let col: i32 = parts[2].parse().unwrap_or(-1);
                    if row >= 0 && row < 8 && col >= 0 && col < 8 {
                        println!("Pressing key at ({}, {})", row, col);
                        unsafe { emu_core::emu_set_key(emu, row, col, 1) };
                        dump_keypad_row(emu, row as u32);
                    } else {
                        println!("Invalid row/col (must be 0-7)");
                    }
                } else {
                    println!("Usage: p <row> <col>");
                }
            }
            "r" | "release" => {
                if parts.len() >= 3 {
                    let row: i32 = parts[1].parse().unwrap_or(-1);
                    let col: i32 = parts[2].parse().unwrap_or(-1);
                    if row >= 0 && row < 8 && col >= 0 && col < 8 {
                        println!("Releasing key at ({}, {})", row, col);
                        unsafe { emu_core::emu_set_key(emu, row, col, 0) };
                        dump_keypad_row(emu, row as u32);
                    } else {
                        println!("Invalid row/col (must be 0-7)");
                    }
                } else {
                    println!("Usage: r <row> <col>");
                }
            }
            "d" | "dump" => {
                dump_all_keypad_data(emu);
            }
            "s" | "status" => {
                dump_keypad_status(emu);
            }
            "c" | "cycles" => {
                let cycles: i32 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(1000);
                println!("Running {} cycles...", cycles);
                let executed = unsafe { emu_core::emu_run_cycles(emu, cycles) };
                println!("Executed {} cycles", executed);
            }
            "q" | "quit" => {
                break;
            }
            _ => {
                println!("Unknown command: {}", parts[0]);
            }
        }
    }

    // Cleanup
    unsafe { emu_core::emu_destroy(emu) };
    println!("Goodbye!");
}

extern "C" fn log_callback(msg: *const std::os::raw::c_char) {
    if !msg.is_null() {
        let s = unsafe { std::ffi::CStr::from_ptr(msg) };
        println!("[EMU] {}", s.to_string_lossy());
    }
}

fn dump_keypad_row(emu: *mut emu_core::Emu, row: u32) {
    // Read the keypad data register for this row
    // Keypad data is at 0xF50010 + row*2
    let addr = 0xF50010 + row * 2;

    // We need to use the bus to read - but we can't access it directly from FFI
    // For now, just print what we expect
    println!("  Row {} data register at 0x{:06X}", row, addr);
}

fn dump_all_keypad_data(_emu: *mut emu_core::Emu) {
    println!("Keypad Data Registers (0xF50010 - 0xF5002F):");
    println!("  (Note: Direct register reading requires bus access)");
    println!("  Use the integration tests in keypad_integration_test.rs for detailed verification");
}

fn dump_keypad_status(_emu: *mut emu_core::Emu) {
    println!("Keypad Status Registers:");
    println!("  Control (0xF50000): configures scanning mode");
    println!("  Size (0xF50004): 0x88 = 8x8 matrix");
    println!("  Status (0xF50008): interrupt status bits");
    println!("  Int Mask (0xF5000C): interrupt enable mask");
    println!("  (Note: Direct register reading requires bus access)");
}

// FFI declarations
mod emu_core {
    use std::os::raw::c_char;

    #[repr(C)]
    pub struct Emu {
        _private: [u8; 0],
    }

    extern "C" {
        pub fn emu_create() -> *mut Emu;
        pub fn emu_destroy(emu: *mut Emu);
        pub fn emu_set_log_callback(cb: Option<extern "C" fn(*const c_char)>);
        pub fn emu_set_key(emu: *mut Emu, row: i32, col: i32, down: i32);
        pub fn emu_run_cycles(emu: *mut Emu, cycles: i32) -> i32;
    }
}
