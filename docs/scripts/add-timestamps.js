import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const docsDir = path.join(__dirname, '..', 'src', 'content', 'docs');

function getAllMdFiles(dir) {
  const files = [];
  const items = fs.readdirSync(dir);
  for (const item of items) {
    const fullPath = path.join(dir, item);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      files.push(...getAllMdFiles(fullPath));
    } else if (item.endsWith('.md')) {
      files.push(fullPath);
    }
  }
  return files;
}

function getChangedFiles() {
  try {
    // Get list of changed files (staged + unstaged)
    const changedFiles = execSync('git diff --name-only HEAD', { encoding: 'utf8' })
      .split('\n')
      .filter(file => file.trim() !== '');

    // Get list of staged files
    const stagedFiles = execSync('git diff --cached --name-only', { encoding: 'utf8' })
      .split('\n')
      .filter(file => file.trim() !== '');

    // Get list of untracked files (new files not yet added to git)
    const untrackedFiles = execSync('git ls-files --others --exclude-standard', { encoding: 'utf8' })
      .split('\n')
      .filter(file => file.trim() !== '');

    // Combine all lists and remove duplicates
    const allChangedFiles = [...new Set([...changedFiles, ...stagedFiles, ...untrackedFiles])];

    // Convert to absolute paths
    return allChangedFiles.map(file => path.resolve(file));
  } catch (error) {
    console.warn('Warning: Could not get git status. Falling back to all files.');
    console.warn('Make sure you are in a git repository.');
    return null; // Return null if unable to get git status
  }
}

function formatTimestamp(date) {
  const dateStr = date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
  const timeStr = date.toLocaleTimeString('en-US', { hour12: false });
  return `<br>\n<br>\n\n---\n\n*Last update: ${dateStr} ${timeStr}*`;
}

function parseTimestamp(content) {
  // Updated regex to match both old and new formats
  const oldFormatMatch = content.match(/---\s*\n\s*\*Last update: (.+?)\*/);
  const newFormatMatch = content.match(/<br>\s*\n\s*<br>\s*\n\s*---\s*\n\s*\*Last update: (.+?)\*/);

  if (newFormatMatch) {
    const dateStr = newFormatMatch[1];
    return new Date(dateStr);
  } else if (oldFormatMatch) {
    const dateStr = oldFormatMatch[1];
    return new Date(dateStr);
  }
  return null;
}

function removeExistingTimestamp(content) {
  // Remove old format: ---\n*Last update: ...*
  content = content.replace(/\n*---\s*\n\s*\*Last update: .+?\*\s*$/g, '');

  // Remove new format: <br><br>\n---\n*Last update: ...*
  content = content.replace(/\n*<br>\s*\n\s*<br>\s*\n\s*---\s*\n\s*\*Last update: .+?\*\s*$/g, '');

  return content.trim();
}

const allMdFiles = getAllMdFiles(docsDir);
const changedFiles = getChangedFiles();

// If unable to get changed files, process all .md files
const filesToProcess = changedFiles
  ? allMdFiles.filter(file => {
    const absoluteFile = path.resolve(file);
    return changedFiles.includes(absoluteFile);
  })
  : allMdFiles;

console.log(`Found ${filesToProcess.length} changed .md files to process`);

filesToProcess.forEach(file => {
  const stat = fs.statSync(file);
  const mtime = stat.mtime;
  const content = fs.readFileSync(file, 'utf-8');
  const currentTimestamp = parseTimestamp(content);
  const newTimestampStr = formatTimestamp(mtime);

  if (!currentTimestamp || currentTimestamp.getTime() !== mtime.getTime()) {
    // Remove any existing timestamp first
    const cleanContent = removeExistingTimestamp(content);

    // Append new format timestamp
    const newContent = cleanContent + '\n\n' + newTimestampStr;

    fs.writeFileSync(file, newContent);
    console.log(`Updated timestamp in ${file} to new format`);
  } else {
    console.log(`No change needed for ${file}`);
  }
});

console.log('Done updating timestamps.');