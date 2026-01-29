//! SHA256 Accelerator Stub
//!
//! Memory-mapped at port 0x2xxx (I/O port address space)
//!
//! Register layout (from CEmu sha256.c):
//! - 0x00: Control register (write triggers operations)
//! - 0x0C: state[7] - lowest hash word for quick read
//! - 0x10-0x4F: block[0-15] - 64 bytes of input data (16 x 32-bit words)
//! - 0x60-0x7F: state[0-7] - 32 bytes of hash output (8 x 32-bit words)
//!
//! This is a minimal stub that accepts writes but doesn't compute real hashes.
//! The ROM checks for peripheral presence but doesn't rely on hash results during boot.

/// SHA256 accelerator controller (stub)
#[derive(Debug, Clone)]
pub struct Sha256Controller {
    /// Input block (64 bytes / 16 words)
    block: [u32; 16],
    /// Hash state (32 bytes / 8 words)
    state: [u32; 8],
    /// Last accessed index (for protected port behavior)
    last: u16,
}

impl Sha256Controller {
    /// Initial SHA256 state (standard IV)
    const INITIAL_STATE: [u32; 8] = [
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ];

    /// Create a new SHA256 controller
    pub fn new() -> Self {
        Self {
            block: [0; 16],
            state: Self::INITIAL_STATE,
            last: 0,
        }
    }

    /// Reset the controller
    pub fn reset(&mut self) {
        self.block = [0; 16];
        self.state = Self::INITIAL_STATE;
        self.last = 0;
    }

    /// Read a byte from the SHA256 registers
    /// addr is offset within 0x2xxx range (0x00-0xFF typically)
    pub fn read(&self, addr: u32) -> u8 {
        let index = (addr >> 2) as usize;
        let bit_offset = ((addr & 3) * 8) as u32;

        if index == 0x0C >> 2 {
            // Quick access to state[7]
            ((self.state[7] >> bit_offset) & 0xFF) as u8
        } else if index >= 0x10 >> 2 && index < 0x50 >> 2 {
            // Block data (0x10-0x4F)
            let block_idx = index - (0x10 >> 2);
            if block_idx < 16 {
                ((self.block[block_idx] >> bit_offset) & 0xFF) as u8
            } else {
                0
            }
        } else if index >= 0x60 >> 2 && index < 0x80 >> 2 {
            // State data (0x60-0x7F)
            let state_idx = index - (0x60 >> 2);
            if state_idx < 8 {
                ((self.state[state_idx] >> bit_offset) & 0xFF) as u8
            } else {
                0
            }
        } else {
            0
        }
    }

    /// Write a byte to the SHA256 registers
    /// addr is offset within 0x2xxx range
    pub fn write(&mut self, addr: u32, value: u8) {
        let index = (addr >> 2) as usize;
        let bit_offset = ((addr & 3) * 8) as u32;

        if addr == 0 {
            // Control register at 0x00
            // CEmu: byte & 0x10 clears state, 0x0A/0x0B initializes, 0x0E/0x0F processes
            if value & 0x10 != 0 {
                // Clear state
                self.state = [0; 8];
            } else if (value & 0x0E) == 0x0A {
                // Initialize (first block)
                self.state = Self::INITIAL_STATE;
            }
            // Note: We don't actually compute hashes - just accept the writes
            // If boot needs real hashes, we'd implement process_block() here
        } else if index >= 0x10 >> 2 && index < 0x50 >> 2 {
            // Block data (0x10-0x4F)
            let block_idx = index - (0x10 >> 2);
            if block_idx < 16 {
                let mask = !(0xFF << bit_offset);
                self.block[block_idx] = (self.block[block_idx] & mask) | ((value as u32) << bit_offset);
            }
        }
        // State registers are read-only
    }
}

impl Default for Sha256Controller {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_new() {
        let sha = Sha256Controller::new();
        assert_eq!(sha.state, Sha256Controller::INITIAL_STATE);
        assert_eq!(sha.block, [0; 16]);
    }

    #[test]
    fn test_reset() {
        let mut sha = Sha256Controller::new();
        sha.block[0] = 0x12345678;
        sha.state[0] = 0xDEADBEEF;
        sha.reset();
        assert_eq!(sha.state, Sha256Controller::INITIAL_STATE);
        assert_eq!(sha.block, [0; 16]);
    }

    #[test]
    fn test_read_state() {
        let sha = Sha256Controller::new();
        // state[7] at 0x0C should be 0x5be0cd19
        assert_eq!(sha.read(0x0C), 0x19);
        assert_eq!(sha.read(0x0D), 0xcd);
        assert_eq!(sha.read(0x0E), 0xe0);
        assert_eq!(sha.read(0x0F), 0x5b);
    }

    #[test]
    fn test_write_block() {
        let mut sha = Sha256Controller::new();
        // Write to block[0] at 0x10
        sha.write(0x10, 0x78);
        sha.write(0x11, 0x56);
        sha.write(0x12, 0x34);
        sha.write(0x13, 0x12);
        assert_eq!(sha.block[0], 0x12345678);
    }

    #[test]
    fn test_control_initialize() {
        let mut sha = Sha256Controller::new();
        sha.state[0] = 0;
        // Write 0x0A to control to initialize
        sha.write(0x00, 0x0A);
        assert_eq!(sha.state, Sha256Controller::INITIAL_STATE);
    }

    #[test]
    fn test_control_clear() {
        let mut sha = Sha256Controller::new();
        // Write 0x10 to control to clear state
        sha.write(0x00, 0x10);
        assert_eq!(sha.state, [0; 8]);
    }
}
