/**
 * OpenConverter History Manager.
 * Persists history to a JSONL file in the user's application data directory.
 * Keeps a maximum of 500 records, newest first (on retrieval).
 */
const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

class HistoryStore {
  constructor(userDataDir, maxEntries = 500) {
    this.filePath = path.join(userDataDir, 'history.jsonl');
    this.maxEntries = maxEntries;
  }

  /**
   * Appends a record to the history file.
   */
  async append(record) {
    try {
      const line = JSON.stringify(record) + '\n';
      await fs.promises.appendFile(this.filePath, line, 'utf8');
      await this.trimIfNeeded();
    } catch (e) {
      console.error('Failed to append to history:', e);
    }
  }

  /**
   * Reads all history records, returning them reversed (newest first).
   */
  async readAll() {
    if (!fs.existsSync(this.filePath)) {
      return [];
    }
    try {
      const fileStream = fs.createReadStream(this.filePath, 'utf8');
      const rl = readline.createInterface({
        input: fileStream,
        crlfDelay: Infinity
      });

      const records = [];
      for await (const line of rl) {
        if (line.trim()) {
          try {
            records.push(JSON.parse(line));
          } catch (e) {
            // Ignore malformed lines
          }
        }
      }
      return records.reverse();
    } catch (e) {
      console.error('Failed to read history:', e);
      return [];
    }
  }

  /**
   * Clears the entire history log.
   */
  async clear() {
    try {
      if (fs.existsSync(this.filePath)) {
        await fs.promises.unlink(this.filePath);
      }
    } catch (e) {
      console.error('Failed to clear history:', e);
    }
  }

  /**
   * Trims the history log if it exceeds the maximum entry size.
   */
  async trimIfNeeded() {
    try {
      if (!fs.existsSync(this.filePath)) return;
      const content = await fs.promises.readFile(this.filePath, 'utf8');
      const lines = content.split('\n').filter((l) => l.trim() !== '');
      if (lines.length > this.maxEntries) {
        const trimmed = lines.slice(-this.maxEntries).join('\n') + '\n';
        await fs.promises.writeFile(this.filePath, trimmed, 'utf8');
      }
    } catch (e) {
      console.error('Failed to trim history:', e);
    }
  }
}

module.exports = HistoryStore;
