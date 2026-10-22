// Export a downloaded ChatGPT share page to Markdown and a basic Word document.
const fs = require('node:fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const marker = 'streamController.enqueue(';
const start = html.indexOf(marker) + marker.length;
let end = start + 1;
for (; end < html.length; end++) {
  if (html[end] === '\\') { end++; continue; }
  if (html[end] === '"') break;
}
const values = JSON.parse(JSON.parse(html.slice(start, end + 1)));
const cache = new Map();
function decode(i) {
  if (i < 0) return undefined;
  if (cache.has(i)) return cache.get(i);
  const value = values[i];
  if (!value || typeof value !== 'object') return value;
  const out = Array.isArray(value) ? [] : {};
  cache.set(i, out);
  if (Array.isArray(value)) value.forEach(x => out.push(decode(x)));
  else for (const [key, ref] of Object.entries(value)) out[key.startsWith('_') ? decode(Number(key.slice(1))) : key] = decode(ref);
  return out;
}
const root = decode(0);
const route = root.loaderData['routes/share.$shareId.($action)'];
const data = route.serverResponse.data;
if (process.argv.includes('--inspect')) {
  console.log(Object.keys(data));
  console.log(JSON.stringify(data.mapping).slice(0,6000));
  process.exit();
}
const mapping = data.mapping;
let node = mapping[data.current_node];
const chain = [];
while (node) { chain.push(node); node = mapping[node.parent]; }
chain.reverse();
const messages = chain.map(n => n.message).filter(m => m && ['user', 'assistant'].includes(m.author.role) && !m.metadata?.is_visually_hidden_from_conversation && (!m.recipient || m.recipient === 'all') && m.content.content_type !== 'thoughts');
const title = data.title || 'Shared conversation';
const source = 'https://chatgpt.com/share/' + route.sharedConversationId;
const sections = [{ heading: title, text: 'Source: ' + source }];
for (const message of messages) {
  const parts = message.content.parts || [];
  const text = parts.map(p => typeof p === 'string' ? p : '[Non-text content: ' + JSON.stringify(p) + ']').join('\n');
  if (text) sections.push({ heading: message.author.role === 'user' ? 'User' : 'ChatGPT', text });
}
fs.mkdirSync('docs', { recursive: true });
fs.writeFileSync('docs/shared-chat-conversation.md', sections.map((s,i) => `${i ? '##' : '#'} ${s.heading}\n\n${s.text}\n`).join('\n'));
const escape = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const paragraph = (text, bold = false) => `<w:p><w:r>${bold ? '<w:rPr><w:b/></w:rPr>' : ''}<w:t xml:space="preserve">${escape(text)}</w:t></w:r></w:p>`;
const body = sections.map(s => paragraph(s.heading, true) + s.text.split('\n').map(line => paragraph(line)).join('')).join('');
const files = {
  '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
  '_rels/.rels': '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
  'word/document.xml': `<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${body}<w:sectPr/></w:body></w:document>`
};
function crc32(buf) {
  let crc = 0xffffffff;
  for (const byte of buf) { crc ^= byte; for (let j=0;j<8;j++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
  return (crc ^ 0xffffffff) >>> 0;
}
const local = [], central = [];
let offset = 0;
for (const [name, text] of Object.entries(files)) {
  const filename = Buffer.from(name), content = Buffer.from(text), crc = crc32(content);
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50); header.writeUInt16LE(20,4); header.writeUInt32LE(crc,14); header.writeUInt32LE(content.length,18); header.writeUInt32LE(content.length,22); header.writeUInt16LE(filename.length,26);
  local.push(header, filename, content);
  const entry = Buffer.alloc(46);
  entry.writeUInt32LE(0x02014b50); entry.writeUInt16LE(20,4); entry.writeUInt16LE(20,6); entry.writeUInt32LE(crc,16); entry.writeUInt32LE(content.length,20); entry.writeUInt32LE(content.length,24); entry.writeUInt16LE(filename.length,28); entry.writeUInt32LE(offset,42);
  central.push(entry, filename);
  offset += header.length + filename.length + content.length;
}
const directory = Buffer.concat(central), trailer = Buffer.alloc(22);
trailer.writeUInt32LE(0x06054b50); trailer.writeUInt16LE(Object.keys(files).length,8); trailer.writeUInt16LE(Object.keys(files).length,10); trailer.writeUInt32LE(directory.length,12); trailer.writeUInt32LE(offset,16);
fs.writeFileSync('docs/shared-chat-conversation.docx', Buffer.concat([...local, directory, trailer]));
console.log(`Exported ${sections.length - 1} messages to docs/shared-chat-conversation.md and .docx`);
