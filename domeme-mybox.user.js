// ==UserScript==
// @name         도매매 마이박스 자동 담기 (판매이력 없는 상품 탐색기)
// @namespace    https://eggthirty.github.io/domeme-nosale-explorer/
// @version      1.1.1
// @description  탐색기에서 [도매매에 담기]를 누르면 상품번호 검색, 해시태그 입력, 마이박스담기까지 자동으로 진행합니다.
// @match        https://domemedb.domeggook.com/index/item/itemDBSearch.php*
// @run-at       document-idle
// @grant        none
// @updateURL    https://eggthirty.github.io/domeme-nosale-explorer/domeme-mybox.user.js
// @downloadURL  https://eggthirty.github.io/domeme-nosale-explorer/domeme-mybox.user.js
// ==/UserScript==

(function () {
  'use strict';
  var QUEUE = 'nsxQueue';     // 로그인 전에 받은 작업 (로그인 후 이어서 진행)
  var PENDING = 'nsxMybox';   // 검색을 눌렀고 결과를 기다리는 작업 (북마크와 같은 키)
  var MAX_AGE = 10 * 60 * 1000;

  function $(id) { return document.getElementById(id); }
  function btn(label, cls) {
    return [].slice.call(document.querySelectorAll('button, input[type=button], a')).filter(function (b) {
      return (b.innerText || b.value || '').trim() === label && (!cls || b.classList.contains(cls));
    })[0];
  }
  function fire(el, type) { el.dispatchEvent(new Event(type, { bubbles: true })); }
  function setChecked(el, on) { if (el && el.checked !== on) el.click(); }
  // 검색 결과의 상품 줄 체크박스 (name="item[]", 누르면 도매매가 '선택상품수'를 센다)
  function resultBoxes() { return [].slice.call(document.querySelectorAll('input[name="item[]"]')); }
  function selectedCount() { var m = document.body.innerText.match(/선택상품수\s*(\d+)/); return m ? Number(m[1]) : null; }
  function loggedIn() {
    var add = btn('추가', 'n_button2');
    return !!add && !/loginGo/.test(add.getAttribute('onclick') || '');
  }
  function read(key) {
    try { var v = JSON.parse(sessionStorage.getItem(key)); return v && Date.now() - v.at < MAX_AGE ? v : null; } catch (e) { return null; }
  }
  function save(key, v) { v.at = Date.now(); sessionStorage.setItem(key, JSON.stringify(v)); }

  // 화면 왼쪽 아래 진행 상황 안내
  var box;
  var NSX_FONT = '"Apple SD Gothic Neo","Malgun Gothic","맑은 고딕",sans-serif';
  // 도매매 CSS가 섞이지 않도록 스타일은 nsx- 접두어 + 루트 all:initial 로 격리
  function nsxStyle() {
    if (document.getElementById('nsx-style')) return;
    var st = document.createElement('style');
    st.id = 'nsx-style';
    st.textContent =
      '.nsx-card,.nsx-card *{all:initial;box-sizing:border-box;font-family:' + NSX_FONT + ';}' +
      '.nsx-card{position:fixed;left:16px;bottom:96px;z-index:2147483000;display:block;width:340px;max-width:calc(100vw - 32px);' +
        'background:#FFFFFF;color:#121722;border:1px solid #DDE2EA;border-left:4px solid #2047D6;border-radius:10px;overflow:hidden;' +
        'box-shadow:0 12px 32px rgba(18,23,34,.18),0 2px 6px rgba(18,23,34,.08);font-size:14px;line-height:1.55;}' +
      '.nsx-head{display:flex;align-items:center;gap:8px;padding:9px 14px;background:#2047D6;color:#FFFFFF;font-size:12px;font-weight:700;letter-spacing:.01em;line-height:1.4;}' +
      '.nsx-dot{display:inline-block;flex:none;width:8px;height:8px;border-radius:50%;background:#FFC83D;animation:nsx-pulse 1.2s ease-in-out infinite;}' +
      '.nsx-dot.nsx-ok{animation:none;background:#FFFFFF;opacity:.85;}' +
      '.nsx-title{display:inline;color:#FFFFFF;font-size:12px;font-weight:700;}' +
      '.nsx-title .nsx-sub{display:inline;color:#FFFFFF;opacity:.78;font-weight:500;font-size:12px;}' +
      '.nsx-body{display:block;padding:12px 14px 13px;color:#121722;font-size:14px;line-height:1.6;word-break:keep-all;overflow-wrap:anywhere;}' +
      '.nsx-num{display:inline;padding:0 5px;border-radius:4px;background:#FFC83D;color:#3A2800;font-weight:700;font-size:13px;}' +
      '.nsx-foot{display:flex;justify-content:flex-end;padding:0 14px 12px;}' +
      '.nsx-close{display:inline-block;cursor:pointer;padding:5px 14px;border:1px solid #DDE2EA;border-radius:6px;background:#FFFFFF;color:#121722;font-size:13px;font-weight:600;line-height:1.4;}' +
      '.nsx-close:hover{background:#F3F5F8;border-color:#5B6475;}' +
      '.nsx-close:focus-visible{outline:2px solid #2047D6;outline-offset:2px;}' +
      '@keyframes nsx-pulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.35;transform:scale(.75)}}' +
      '@media (prefers-reduced-motion: reduce){.nsx-dot{animation:none;}}';
    (document.head || document.documentElement).appendChild(st);
  }
  function nsxHtml(text) {
    // 호출부는 <br> 만 쓴다: 나머지는 이스케이프하고, 'N개' 숫자는 노란 강조
    return String(text).replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; })
      .replace(/&#60;br&#62;/g, '<br>')
      .replace(/(\d+)개/g, '<span class="nsx-num">$1</span>개');
  }
  function say(text, done) {
    nsxStyle();
    if (!box) {
      box = document.createElement('div');
      box.className = 'nsx-card';
      box.setAttribute('role', 'status');
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    box.innerHTML =
      '<div class="nsx-head"><span class="nsx-dot' + (done ? ' nsx-ok' : '') + '"></span>' +
      '<span class="nsx-title">스피드고잡스 PICK ITEM <span class="nsx-sub">· 마이박스 자동 담기</span></span></div>' +
      '<div class="nsx-body">' + nsxHtml(text) + '</div>' +
      (done ? '<div class="nsx-foot"><button type="button" class="nsx-close">닫기</button></div>' : '');
    var c = box.querySelector('button');
    if (c) c.onclick = function () { box.remove(); box = null; };
  }

  function payloadFromHash() {
    var m = location.hash.match(/nsx=([^&]+)/);
    if (!m) return null;
    history.replaceState(null, '', location.pathname + location.search);
    try {
      var p = JSON.parse(decodeURIComponent(m[1]));
      var nos = (p.n || []).map(String).filter(function (n) { return /^\d{5,}$/.test(n); });
      return nos.length ? { tag: p.t || '', nos: nos } : null;
    } catch (e) { return null; }
  }

  function finish(job) {
    var boxes = resultBoxes();
    sessionStorage.removeItem(PENDING);
    if (!boxes.length) { say('검색된 상품이 없습니다. 품절되었거나 판매가 끝난 상품일 수 있습니다.', true); return; }
    boxes.forEach(function (b) { setChecked(b, true); });
    var tag = $('keywordTags');
    if (tag && job.tag) { tag.value = job.tag; fire(tag, 'input'); fire(tag, 'change'); }
    var mb = btn('마이박스담기');
    if (!mb) { say('마이박스담기 버튼을 찾지 못했습니다.', true); return; }
    setTimeout(function () {
      var picked = selectedCount();
      say('보낸 ' + job.n + '개 중 ' + boxes.length + '개가 검색되어 ' + (picked == null ? boxes.length : picked) + '개를 마이박스에 담습니다' + (job.tag ? ' (' + job.tag + ')' : '') + '.' +
        (job.n > boxes.length ? '<br>빠진 상품은 품절되었거나 판매가 끝난 상품일 수 있습니다.' : ''), true);
      if (window.__nsxDryRun) return;
      mb.click();
    }, 400);
  }

  function start(job) {
    if (!loggedIn()) {
      save(QUEUE, job);
      say('도매매에 로그인하면 이어서 진행합니다. 로그인 후 이 화면으로 돌아오세요.', true);
      return;
    }
    sessionStorage.removeItem(QUEUE);
    say('상품번호 ' + job.nos.length + '개로 검색하는 중…');
    var sf = $('sf');
    if (sf) { sf.value = 'no'; fire(sf, 'change'); }
    var ta = $('search');
    ta.value = job.nos.join('\n'); fire(ta, 'input');
    var add = btn('추가', 'n_button2');
    if (add) add.click();
    // 수량별비례상품 제외 · 구간별배송료추가상품 제외는 체크 해제한 채로 검색한다
    setChecked($('pp1'), false);
    setChecked($('pp2'), false);
    // 결과를 한 화면에 모두 보이게 (도매매는 최대 200개씩 보기)
    var form = $('search').form;
    var limit = form && form.querySelector('input[name=pageLimit]');
    if (limit) limit.value = job.nos.length > 100 ? '200' : job.nos.length > 50 ? '100' : '50';
    var pending = { tag: job.tag, n: job.nos.length };
    save(PENDING, pending);
    var search = btn('검색', 'button13');
    if (!search) { say('검색 버튼을 찾지 못했습니다.', true); return; }
    var before = resultBoxes().length;
    setTimeout(function () {
      search.click();
      // 결과가 같은 화면에 뜨는 경우: 기다렸다가 이어서 담는다. 화면이 새로 열리면 아래 resume()이 처리한다.
      var tries = 0;
      var timer = setInterval(function () {
        tries++;
        var n = resultBoxes().length;
        if (n && n !== before) { clearInterval(timer); setTimeout(function () { if (read(PENDING)) finish(pending); }, 600); }
        else if (tries > 40) clearInterval(timer);
      }, 500);
    }, 400);
  }

  function resume() {
    var job = payloadFromHash();
    if (job) { start(job); return; }
    var pending = read(PENDING);
    if (pending && resultBoxes().length) { setTimeout(function () { finish(pending); }, 600); return; }
    var queued = read(QUEUE);
    if (queued && loggedIn()) start(queued);
  }

  if (!$('search')) return;
  resume();
})();
