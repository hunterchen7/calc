//! ST7789V LCD Panel Stub
//!
//! Minimal stub for the SPI-connected LCD panel (ST7789V) on the TI-84 CE.
//! The panel receives 9-bit SPI frames where bit 8 selects command (0) vs data (1).
//!
//! During boot, the ROM sends initialization commands (sleep out, display on,
//! pixel format, etc.) but does not read status back. This stub absorbs
//! commands and stores key register values for future use.
//!
//! Reference: CEmu panel.c / panel.h

/// ST7789V commands used during initialization
#[allow(dead_code)]
mod cmd {
    pub const NOP: u8 = 0x00;
    pub const SWRESET: u8 = 0x01;
    pub const SLPIN: u8 = 0x10;
    pub const SLPOUT: u8 = 0x11;
    pub const INVOFF: u8 = 0x20;
    pub const INVON: u8 = 0x21;
    pub const DISPOFF: u8 = 0x28;
    pub const DISPON: u8 = 0x29;
    pub const CASET: u8 = 0x2A;
    pub const RASET: u8 = 0x2B;
    pub const RAMWR: u8 = 0x2C;
    pub const RAMWRC: u8 = 0x3C;
    pub const MADCTL: u8 = 0x36;
    pub const COLMOD: u8 = 0x3A;
}

/// Panel stub state
#[derive(Debug, Clone)]
pub struct PanelStub {
    /// Current command being processed
    current_cmd: u8,
    /// Parameter index for multi-byte commands
    param_idx: u8,
    /// Expected parameter count for current command
    param_count: u8,
    /// Whether the display is sleeping
    sleeping: bool,
    /// Whether the display is on
    display_on: bool,
    /// Whether inversion is enabled
    inverted: bool,
    /// Memory access control (MADCTL)
    madctl: u8,
    /// Pixel format (COLMOD)
    colmod: u8,
    /// Column address range [start_hi, start_lo, end_hi, end_lo]
    caset: [u8; 4],
    /// Row address range [start_hi, start_lo, end_hi, end_lo]
    raset: [u8; 4],
}

impl PanelStub {
    pub fn new() -> Self {
        Self {
            current_cmd: 0,
            param_idx: 0,
            param_count: 0,
            sleeping: true,
            display_on: false,
            inverted: false,
            madctl: 0,
            colmod: 0,
            caset: [0; 4],
            raset: [0; 4],
        }
    }

    pub fn reset(&mut self) {
        *self = Self::new();
    }

    /// Process a 9-bit SPI frame from the controller.
    /// Bit 8: 0 = command, 1 = data/parameter.
    /// Returns the number of bits in the response frame (always 9).
    pub fn transfer(&mut self, tx_data: u32) -> u8 {
        let is_data = tx_data & 0x100 != 0;
        let byte = (tx_data & 0xFF) as u8;

        if is_data {
            self.write_param(byte);
        } else {
            self.write_cmd(byte);
        }

        9 // Always 9-bit frames
    }

    /// Process a command byte
    fn write_cmd(&mut self, cmd: u8) {
        self.current_cmd = cmd;
        self.param_idx = 0;

        // Determine expected parameter count for this command
        self.param_count = match cmd {
            cmd::NOP | cmd::SWRESET => 0,
            cmd::SLPIN => { self.sleeping = true; 0 }
            cmd::SLPOUT => { self.sleeping = false; 0 }
            cmd::INVOFF => { self.inverted = false; 0 }
            cmd::INVON => { self.inverted = true; 0 }
            cmd::DISPOFF => { self.display_on = false; 0 }
            cmd::DISPON => { self.display_on = true; 0 }
            cmd::CASET => 4,
            cmd::RASET => 4,
            cmd::MADCTL => 1,
            cmd::COLMOD => 1,
            cmd::RAMWR | cmd::RAMWRC => 0, // Variable length, absorb until next command
            _ => 0xFF, // Unknown command — absorb all params until next command
        };

        if cmd == cmd::SWRESET {
            self.reset();
        }
    }

    /// Process a parameter byte for the current command
    fn write_param(&mut self, param: u8) {
        if self.param_count == 0 {
            return; // No parameters expected or already consumed
        }

        match self.current_cmd {
            cmd::CASET => {
                if (self.param_idx as usize) < self.caset.len() {
                    self.caset[self.param_idx as usize] = param;
                }
            }
            cmd::RASET => {
                if (self.param_idx as usize) < self.raset.len() {
                    self.raset[self.param_idx as usize] = param;
                }
            }
            cmd::MADCTL => {
                self.madctl = param;
            }
            cmd::COLMOD => {
                self.colmod = param;
            }
            _ => {} // Absorb unknown parameters
        }

        self.param_idx += 1;
        if self.param_idx >= self.param_count && self.param_count != 0xFF {
            self.param_count = 0; // Done with parameters
        }
    }
}

impl Default for PanelStub {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_new() {
        let panel = PanelStub::new();
        assert!(panel.sleeping);
        assert!(!panel.display_on);
    }

    #[test]
    fn test_sleep_out() {
        let mut panel = PanelStub::new();
        assert!(panel.sleeping);
        panel.transfer(cmd::SLPOUT as u32); // Command (bit 8 = 0)
        assert!(!panel.sleeping);
    }

    #[test]
    fn test_display_on() {
        let mut panel = PanelStub::new();
        panel.transfer(cmd::DISPON as u32);
        assert!(panel.display_on);
    }

    #[test]
    fn test_colmod() {
        let mut panel = PanelStub::new();
        panel.transfer(cmd::COLMOD as u32); // Command
        panel.transfer(0x100 | 0x55); // Data: 16bpp (RGB565)
        assert_eq!(panel.colmod, 0x55);
    }

    #[test]
    fn test_caset() {
        let mut panel = PanelStub::new();
        panel.transfer(cmd::CASET as u32); // Command
        panel.transfer(0x100 | 0x00); // Start MSB
        panel.transfer(0x100 | 0x00); // Start LSB
        panel.transfer(0x100 | 0x01); // End MSB
        panel.transfer(0x100 | 0x3F); // End LSB (319)
        assert_eq!(panel.caset, [0x00, 0x00, 0x01, 0x3F]);
    }

    #[test]
    fn test_reset_cmd() {
        let mut panel = PanelStub::new();
        panel.transfer(cmd::SLPOUT as u32);
        panel.transfer(cmd::DISPON as u32);
        assert!(!panel.sleeping);
        assert!(panel.display_on);

        panel.transfer(cmd::SWRESET as u32);
        assert!(panel.sleeping);
        assert!(!panel.display_on);
    }

    #[test]
    fn test_frame_length() {
        let mut panel = PanelStub::new();
        assert_eq!(panel.transfer(0x00), 9); // Always 9-bit
        assert_eq!(panel.transfer(0x100), 9);
    }
}
