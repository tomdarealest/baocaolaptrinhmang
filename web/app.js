'use strict';
const $ = id => document.getElementById(id);
let authorization = '';
let busy = false;
const maxUpload = 20 * 1024 * 1024;
function message(text, error = false) { $('status').textContent = text; $('status').classList.toggle('error', error); }
function size(bytes) { return bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KiB` : `${(bytes / 1048576).toFixed(1)} MiB`; }
function lock(value) { busy = value; document.querySelectorAll('button, input').forEach(el => el.disabled = value); }
function logout() { authorization = ''; $('password').value = ''; $('files').replaceChildren(); $('workspace').hidden = true; $('loginPanel').hidden = false; $('progress').hidden = true; $('transferStatus').textContent = ''; }
async function request(url, options = {}) {
  const response = await fetch(url, { ...options, headers: { ...options.headers, Authorization: authorization }, cache: 'no-store' });
  if (!response.ok) {
    if (response.status === 401) logout();
    throw new Error(await response.text() || `Lỗi HTTP ${response.status}`);
  }
  return response;
}
async function refresh() {
  const files = await (await request('/api/files')).json();
  $('files').replaceChildren();
  $('fileCount').textContent = `${files.length} tệp trong thư viện`;
  $('empty').hidden = files.length !== 0;
  for (const file of files) {
    const row = document.createElement('li');
    const info = document.createElement('div'); info.className = 'file-info';
    const name = document.createElement('span'); name.className = 'file-name'; name.textContent = file.name;
    const meta = document.createElement('span'); meta.className = 'file-meta'; meta.textContent = `${size(file.size)} · ${new Date(file.modified).toLocaleString('vi-VN')}`;
    const button = document.createElement('button'); button.className = 'secondary'; button.textContent = 'Tải xuống ↓'; button.disabled = busy;
    button.addEventListener('click', () => download(file));
    info.append(name, meta); row.append(info, button); $('files').append(row);
  }
}
$('loginForm').addEventListener('submit', async event => {
  event.preventDefault(); if (busy) return; lock(true); message('Đang kết nối…');
  const bytes = new TextEncoder().encode(`${$('username').value}:${$('password').value}`);
  authorization = 'Basic ' + btoa(Array.from(bytes, b => String.fromCharCode(b)).join(''));
  try { await refresh(); $('loginPanel').hidden = true; $('workspace').hidden = false; $('password').value = ''; message('Đã kết nối. Bạn có thể bắt đầu chia sẻ tệp.'); }
  catch (error) { authorization = ''; message(error.message, true); }
  finally { lock(false); }
});
$('logout').addEventListener('click', () => { if (!busy) { logout(); message('Đã đăng xuất khỏi trình duyệt này.'); } });
$('refresh').addEventListener('click', async () => { if (busy) return; lock(true); try { await refresh(); message('Danh sách đã được cập nhật.'); } catch (error) { message(error.message, true); } finally { lock(false); } });
$('uploadForm').addEventListener('submit', event => {
  event.preventDefault(); if (busy) return;
  const file = $('file').files[0]; if (!file) return;
  if (file.size > maxUpload) { message('Tệp vượt quá giới hạn 20 MiB.', true); return; }
  lock(true); message(''); $('progress').hidden = false; $('progress').value = 0;
  $('transferStatus').textContent = `Đang tải lên ${file.name}…`;
  const xhr = new XMLHttpRequest(); xhr.open('POST', '/api/upload?name=' + encodeURIComponent(file.name)); xhr.timeout = 120000;
  xhr.setRequestHeader('Authorization', authorization); xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.upload.onprogress = e => { if (e.lengthComputable) { const percent = Math.round(e.loaded * 100 / e.total); $('progress').value = percent; $('transferStatus').textContent = percent === 100 ? 'Đã gửi dữ liệu, đang chờ máy chủ xác nhận…' : `${percent}% · ${size(e.loaded)} / ${size(e.total)}`; } };
  xhr.onload = async () => {
    try {
      if (xhr.status !== 201) { if (xhr.status === 401) logout(); throw new Error(xhr.responseText || `Lỗi HTTP ${xhr.status}`); }
      $('progress').value = 100; $('transferStatus').textContent = `Đã lưu ${file.name} trên máy chủ.`; $('file').value = ''; message('Tải lên thành công.'); await refresh();
    } catch (error) { $('transferStatus').textContent = 'Không hoàn tất thao tác.'; message(error.message, true); }
    finally { lock(false); }
  };
  const failed = () => { $('transferStatus').textContent = 'Kết nối bị gián đoạn.'; message('Không nhận được xác nhận. Làm mới danh sách trước khi thử tải lại.', true); lock(false); };
  xhr.onerror = failed; xhr.ontimeout = failed; xhr.onabort = failed; xhr.send(file);
});
async function download(file) {
  if (busy) return; lock(true); message(`Đang tải ${file.name}…`);
  try {
    const blob = await (await request('/api/download?name=' + encodeURIComponent(file.name))).blob();
    const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = file.name;
    document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000);
    message(`Đã nhận ${file.name}. Trình duyệt sẽ lưu tệp theo cài đặt tải xuống của bạn.`);
  } catch (error) { message(error.message, true); }
  finally { lock(false); }
}
