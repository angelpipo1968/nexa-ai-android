'use client';

import React, { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X,
  Code2,
  Eye,
  Download,
  Copy,
  Check,
  Loader2,
  Sparkles,
  SplitSquareHorizontal,
  Columns2,
  RotateCcw,
  Maximize2,
  Minimize2,
  FileCode,
  FileText,
  Palette,
  Hash,
  Terminal,
  ChevronDown,
  Zap,
} from 'lucide-react';

interface CanvasModeProps {
  onClose: () => void;
  onSendMessage?: (text: string) => Promise<void>;
  accent: string;
  T: { bg: string; surf: string; border: string; text: string; sec: string; muted: string; inputBg: string };
  resolvedTheme: string;
  initialCode?: string;
}

type SplitMode = 'code' | 'preview' | 'split';
type CanvasFileType = 'html' | 'css' | 'javascript' | 'typescript' | 'json' | 'markdown' | 'python';

interface CanvasFile {
  name: string;
  content: string;
  language: CanvasFileType;
  modified: boolean;
}

const LANG_CONFIG: Record<CanvasFileType, { color: string; label: string }> = {
  html: { color: '#E34F26', label: 'HTML' },
  css: { color: '#1572B6', label: 'CSS' },
  javascript: { color: '#F7DF1E', label: 'JS' },
  typescript: { color: '#3178C6', label: 'TS' },
  json: { color: '#8B5CF6', label: 'JSON' },
  markdown: { color: '#06B6D4', label: 'MD' },
  python: { color: '#3776AB', label: 'PY' },
};

const TEMPLATES: { name: string; desc: string; icon: React.ReactNode; files: CanvasFile[] }[] = [
  {
    name: 'Landing Page',
    desc: 'Pagina moderna con animaciones',
    icon: <Sparkles size={20} />,
    files: [
      {
        name: 'index.html',
        language: 'html',
        modified: false,
        content: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NEXA Canvas</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif; background: #0a0a0f; color: #f0f0f0; overflow-x: hidden; }
    .hero { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 2rem; position: relative; }
    .hero::before { content: ''; position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(circle at 50% 50%, rgba(6,182,212,0.08) 0%, transparent 50%); animation: rotate 20s linear infinite; }
    @keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    .hero h1 { font-size: clamp(2.5rem, 8vw, 5rem); font-weight: 900; letter-spacing: -2px; margin-bottom: 1rem; position: relative; }
    .gradient { background: linear-gradient(135deg, #06B6D4, #8B5CF6, #EC4899); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
    .hero p { font-size: 1.2rem; color: #888; max-width: 600px; line-height: 1.6; position: relative; }
    .btn-group { display: flex; gap: 1rem; margin-top: 2.5rem; position: relative; flex-wrap: wrap; justify-content: center; }
    .btn { padding: 0.875rem 2rem; border-radius: 12px; font-size: 1rem; font-weight: 600; cursor: pointer; transition: all 0.3s; border: none; }
    .btn-primary { background: linear-gradient(135deg, #06B6D4, #8B5CF6); color: white; box-shadow: 0 4px 20px rgba(6,182,212,0.3); }
    .btn-primary:hover { transform: translateY(-2px); box-shadow: 0 8px 30px rgba(6,182,212,0.4); }
    .btn-secondary { background: rgba(255,255,255,0.05); color: #ccc; border: 1px solid rgba(255,255,255,0.1); }
    .btn-secondary:hover { background: rgba(255,255,255,0.1); border-color: rgba(255,255,255,0.2); }
    .features { padding: 6rem 2rem; max-width: 1200px; margin: 0 auto; }
    .features h2 { text-align: center; font-size: 2.5rem; margin-bottom: 3rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.5rem; }
    .card { background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 16px; padding: 2rem; transition: all 0.3s; }
    .card:hover { background: rgba(255,255,255,0.06); border-color: rgba(6,182,212,0.3); transform: translateY(-4px); }
    .card .icon { width: 48px; height: 48px; border-radius: 12px; background: linear-gradient(135deg, rgba(6,182,212,0.15), rgba(139,92,246,0.15)); display: flex; align-items: center; justify-content: center; margin-bottom: 1rem; font-size: 1.5rem; }
    .card h3 { font-size: 1.15rem; margin-bottom: 0.5rem; }
    .card p { color: #888; font-size: 0.95rem; line-height: 1.5; }
  </style>
</head>
<body>
  <section class="hero">
    <h1><span class="gradient">Build the Future</span></h1>
    <p>Create stunning applications with AI-powered tools. From concept to deployment in minutes.</p>
    <div class="btn-group">
      <button class="btn btn-primary">Get Started</button>
      <button class="btn btn-secondary">Learn More</button>
    </div>
  </section>
  <section class="features">
    <h2><span class="gradient">Features</span></h2>
    <div class="grid">
      <div class="card"><div class="icon">&#9889;</div><h3>Lightning Fast</h3><p>Generate production-ready code in seconds with our advanced AI engine.</p></div>
      <div class="card"><div class="icon">&#127912;</div><h3>Beautiful Design</h3><p>Every project comes with modern, responsive design out of the box.</p></div>
      <div class="card"><div class="icon">&#128640;</div><h3>One-Click Deploy</h3><p>Ship your project to the web with a single click. No configuration needed.</p></div>
    </div>
  </section>
</body>
</html>`,
      },
    ],
  },
  {
    name: 'Calculator',
    desc: 'Calculadora funcional',
    icon: <Hash size={20} />,
    files: [
      {
        name: 'index.html',
        language: 'html',
        modified: false,
        content: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Calculator</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: -apple-system, system-ui, sans-serif; background: #0a0a0a; display: flex; align-items: center; justify-content: center; min-height: 100vh; }
    .calc { background: #1a1a1a; border-radius: 24px; padding: 24px; width: 320px; box-shadow: 0 20px 60px rgba(0,0,0,0.5); }
    .display { background: #111; border-radius: 16px; padding: 20px; margin-bottom: 16px; text-align: right; min-height: 100px; display: flex; flex-direction: column; justify-content: flex-end; }
    .expression { color: #666; font-size: 16px; min-height: 24px; word-break: break-all; }
    .result { color: #fff; font-size: 40px; font-weight: 300; word-break: break-all; }
    .buttons { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
    button { border: none; border-radius: 14px; padding: 18px; font-size: 20px; cursor: pointer; transition: all 0.15s; font-weight: 500; }
    button:active { transform: scale(0.95); }
    .num { background: #2a2a2a; color: #fff; }
    .num:hover { background: #333; }
    .op { background: #06B6D420; color: #06B6D4; }
    .op:hover { background: #06B6D430; }
    .eq { background: linear-gradient(135deg, #06B6D4, #8B5CF6); color: #fff; grid-column: span 2; }
    .eq:hover { opacity: 0.9; }
    .fn { background: #1a1a2a; color: #aaa; }
    .fn:hover { background: #222235; }
  </style>
</head>
<body>
  <div class="calc">
    <div class="display">
      <div class="expression" id="expr"></div>
      <div class="result" id="result">0</div>
    </div>
    <div class="buttons">
      <button class="fn" onclick="clearAll()">AC</button>
      <button class="fn" onclick="toggleSign()">+/-</button>
      <button class="fn" onclick="percent()">%</button>
      <button class="op" onclick="addOp('/')">&#247;</button>
      <button class="num" onclick="addNum('7')">7</button>
      <button class="num" onclick="addNum('8')">8</button>
      <button class="num" onclick="addNum('9')">9</button>
      <button class="op" onclick="addOp('*')">&#215;</button>
      <button class="num" onclick="addNum('4')">4</button>
      <button class="num" onclick="addNum('5')">5</button>
      <button class="num" onclick="addNum('6')">6</button>
      <button class="op" onclick="addOp('-')">&#8722;</button>
      <button class="num" onclick="addNum('1')">1</button>
      <button class="num" onclick="addNum('2')">2</button>
      <button class="num" onclick="addNum('3')">3</button>
      <button class="op" onclick="addOp('+')">+</button>
      <button class="num" onclick="addNum('0')">0</button>
      <button class="num" onclick="addDot()">.</button>
      <button class="eq" onclick="calculate()">=</button>
    </div>
  </div>
  <script>
    let current = '0', expression = '', operator = null, waitingForOperand = false;
    function updateDisplay() { document.getElementById('result').textContent = current; document.getElementById('expr').textContent = expression; }
    function addNum(n) { if (waitingForOperand) { current = n; waitingForOperand = false; } else { current = current === '0' ? n : current + n; } updateDisplay(); }
    function addDot() { if (!current.includes('.')) { current += '.'; updateDisplay(); } }
    function addOp(op) { const v = parseFloat(current); if (operator && !waitingForOperand) { const prev = parseFloat(expression); const r = calculateSimple(prev, v, operator); current = String(r); expression = current + ' ' + op + ' '; } else { expression = current + ' ' + op + ' '; } operator = op; waitingForOperand = true; updateDisplay(); }
    function calculateSimple(a, b, op) { switch(op) { case '+': return a + b; case '-': return a - b; case '*': return a * b; case '/': return b !== 0 ? a / b : 'Error'; } return b; }
    function calculate() { if (operator) { const v = parseFloat(current); const prev = parseFloat(expression); const r = calculateSimple(prev, v, operator); expression = expression + current + ' ='; current = String(r); operator = null; waitingForOperand = true; updateDisplay(); } }
    function clearAll() { current = '0'; expression = ''; operator = null; waitingForOperand = false; updateDisplay(); }
    function toggleSign() { current = String(-parseFloat(current)); updateDisplay(); }
    function percent() { current = String(parseFloat(current) / 100); updateDisplay(); }
  </script>
</body>
</html>`,
      },
    ],
  },
  {
    name: 'Blank',
    desc: 'Empezar desde cero',
    icon: <FileText size={20} />,
    files: [
      {
        name: 'index.html',
        language: 'html',
        modified: false,
        content: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My Project</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: system-ui, -apple-system, sans-serif; background: #0a0a0f; color: #f0f0f0; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
    h1 { font-size: 2rem; }
  </style>
</head>
<body>
  <h1>Hello World</h1>
</body>
</html>`,
      },
    ],
  },
];

// ═══════════════════════════════════════════
//  CANVAS MODE COMPONENT
// ═══════════════════════════════════════════

export function CanvasMode({ onClose, onSendMessage, accent, T, resolvedTheme, initialCode }: CanvasModeProps) {
  const [files, setFiles] = useState<CanvasFile[]>([]);
  const [activeFile, setActiveFile] = useState<string>('index.html');
  const [splitMode, setSplitMode] = useState<SplitMode>('split');
  const [isGenerating, setIsGenerating] = useState(false);
  const [aiPrompt, setAiPrompt] = useState('');
  const [showAiBar, setShowAiBar] = useState(false);
  const [copied, setCopied] = useState(false);
  const [showTemplates, setShowTemplates] = useState(true);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [previewKey, setPreviewKey] = useState(0);
  const [showNewFileMenu, setShowNewFileMenu] = useState(false);

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const currentFile = files.find((f) => f.name === activeFile);

  // Handle initial code prop
  useEffect(() => {
    if (initialCode && initialCode.trim()) {
      setFiles([{ name: 'index.html', language: 'html', content: initialCode, modified: false }]);
      setActiveFile('index.html');
      setShowTemplates(false);
    }
  }, [initialCode]);

  // Combine all files into a single HTML document for preview
  const getPreviewContent = useCallback(() => {
    const htmlFile = files.find((f) => f.name.endsWith('.html'));
    if (!htmlFile) {
      const cssFiles = files.filter((f) => f.name.endsWith('.css'));
      const jsFiles = files.filter((f) => f.name.endsWith('.js') || f.name.endsWith('.ts'));
      let html = `<!DOCTYPE html>\n<html lang="en">\n<head>\n  <meta charset="UTF-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n  <title>NEXA Canvas</title>`;
      cssFiles.forEach((f) => { html += `\n  <style>\n${f.content}\n  </style>`; });
      html += `\n</head>\n<body>\n<div id="app"></div>`;
      jsFiles.forEach((f) => { html += `\n  <script>\n${f.content}\n  </script>`; });
      html += `\n</body>\n</html>`;
      return html;
    }

    let content = htmlFile.content;
    const cssFiles = files.filter((f) => f.name.endsWith('.css'));
    cssFiles.forEach((f) => {
      if (!content.includes(f.content)) {
        content = content.replace('</head>', `  <style>\n${f.content}\n  </style>\n</head>`);
      }
    });
    const jsFiles = files.filter((f) => f.name.endsWith('.js') && f.name !== 'index.html');
    jsFiles.forEach((f) => {
      if (!content.includes(f.content)) {
        content = content.replace('</body>', `  <script>\n${f.content}\n  </script>\n</body>`);
      }
    });
    return content;
  }, [files]);

  // Auto-refresh preview on code changes
  useEffect(() => {
    const timer = setTimeout(() => { setPreviewKey((k) => k + 1); }, 300);
    return () => clearTimeout(timer);
  }, [files]);

  const handleCodeChange = (value: string) => {
    setFiles((prev) => prev.map((f) => (f.name === activeFile ? { ...f, content: value, modified: true } : f)));
  };

  const handleTemplateSelect = (template: typeof TEMPLATES[number]) => {
    setFiles(template.files.map((f) => ({ ...f, modified: false })));
    setActiveFile(template.files[0].name);
    setShowTemplates(false);
  };

  const handleAiGenerate = async () => {
    if (!aiPrompt.trim() || isGenerating) return;
    setIsGenerating(true);
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: [{
            role: 'user',
            content: `You are a web developer. Generate a complete, self-contained HTML page based on this description: "${aiPrompt}"

IMPORTANT RULES:
1. Respond with ONLY the HTML code. No explanations, no markdown, no code blocks.
2. Include ALL CSS inside a <style> tag in the <head>.
3. Include ALL JavaScript inside a <script> tag before </body>.
4. Use modern, responsive design with a dark theme (#0a0a0f background).
5. Make it visually impressive with gradients, animations, and smooth transitions.
6. Use system fonts (-apple-system, system-ui, sans-serif).
7. The page must be fully functional and self-contained.
8. Do NOT use any external CDNs or frameworks.`,
          }],
        }),
      });

      if (!response.ok) throw new Error('Failed to generate');

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let fullText = '';

      if (reader) {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          const chunk = decoder.decode(value, { stream: true });
          const lines = chunk.split('\n');
          for (const line of lines) {
            if (line.startsWith('data: ')) {
              try {
                const data = JSON.parse(line.slice(6));
                if (data.text) {
                  fullText += data.text;
                  setFiles((prev) => {
                    const existing = prev.find((f) => f.name === 'index.html');
                    if (existing) {
                      return prev.map((f) => f.name === 'index.html' ? { ...f, content: fullText, modified: true } : f);
                    }
                    return [{ name: 'index.html', language: 'html', content: fullText, modified: true }, ...prev];
                  });
                }
              } catch { /* skip */ }
            }
          }
        }
      }

      // Clean up: remove markdown code blocks if AI added them
      let cleaned = fullText.trim();
      if (cleaned.startsWith('```html')) { cleaned = cleaned.slice(7); }
      else if (cleaned.startsWith('```')) { cleaned = cleaned.slice(3); }
      if (cleaned.endsWith('```')) { cleaned = cleaned.slice(0, -3); }
      cleaned = cleaned.trim();

      setFiles((prev) => prev.map((f) => f.name === 'index.html' ? { ...f, content: cleaned, modified: false } : f));
      setAiPrompt('');
    } catch { /* generation failed silently */ } finally {
      setIsGenerating(false);
    }
  };

  const handleCopyCode = async () => {
    if (!currentFile) return;
    await navigator.clipboard.writeText(currentFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const content = getPreviewContent();
    const blob = new Blob([content], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'project.html'; a.click();
    URL.revokeObjectURL(url);
  };

  const handleAddFile = (name: string, language: CanvasFileType) => {
    if (files.find((f) => f.name === name)) return;
    setFiles((prev) => [...prev, { name, content: '', language, modified: false }]);
    setActiveFile(name);
    setShowNewFileMenu(false);
  };

  const handleDeleteFile = (name: string) => {
    if (files.length <= 1) return;
    setFiles((prev) => prev.filter((f) => f.name !== name));
    if (activeFile === name) {
      setActiveFile(files.find((f) => f.name !== name)?.name || '');
    }
  };

  const toggleFullscreen = () => {
    if (!isFullscreen) { containerRef.current?.requestFullscreen?.(); }
    else { document.exitFullscreen?.(); }
  };

  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', handler);
    return () => document.removeEventListener('fullscreenchange', handler);
  }, []);

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = textareaRef.current.scrollHeight + 'px';
    }
  }, [currentFile?.content, activeFile]);

  // Common button style helper
  const iconBtnStyle = (active?: boolean): React.CSSProperties => ({
    padding: 6, borderRadius: 6, border: 'none', cursor: 'pointer',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    background: active ? `${accent}20` : 'transparent',
    color: active ? accent : T.muted,
    transition: 'all 0.15s',
  });

  // ─── Template Selection Screen ───
  if (showTemplates && files.length === 0) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        style={{ position: 'fixed', inset: 0, zIndex: 50, display: 'flex', flexDirection: 'column', background: T.bg, color: T.text, fontFamily: "'Inter', sans-serif" }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px', height: 56, borderBottom: `1px solid ${T.border}`, background: `${T.surf}CC`, backdropFilter: 'blur(10px)', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: `linear-gradient(135deg, ${accent}, ${accent}88)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Code2 size={16} color="#fff" />
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: accent }}>NEXA Canvas</div>
              <div style={{ fontSize: 10, color: T.muted }}>Code + Live Preview</div>
            </div>
          </div>
          <button onClick={onClose} style={{ padding: 8, borderRadius: 8, border: 'none', background: 'transparent', color: T.muted, cursor: 'pointer', display: 'flex' }}>
            <X size={20} />
          </button>
        </div>

        {/* Templates */}
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
          <div style={{ maxWidth: 520, width: '100%', textAlign: 'center' }}>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
              <div style={{ width: 80, height: 80, margin: '0 auto 24px', borderRadius: 20, background: `${accent}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                <SplitSquareHorizontal size={40} color={accent} />
                <div style={{ position: 'absolute', inset: 0, borderRadius: 20, background: `${accent}20`, filter: 'blur(20px)' }} />
              </div>
              <h3 style={{ fontSize: 24, fontWeight: 800, margin: '0 0 8px', color: accent }}>
                NEXA Canvas
              </h3>
              <p style={{ color: T.muted, fontSize: 14, margin: '0 0 32px', lineHeight: 1.6 }}>
                Escribe codigo con IA y ve el resultado en tiempo real. Lado a lado.
              </p>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 32 }}>
                {TEMPLATES.map((template, i) => (
                  <motion.button
                    key={i}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 + i * 0.08 }}
                    onClick={() => handleTemplateSelect(template)}
                    style={{
                      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: 20,
                      borderRadius: 16, background: T.surf, border: `1px solid ${T.border}`,
                      color: T.text, cursor: 'pointer', transition: 'all 0.2s',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = `${accent}50`; e.currentTarget.style.background = `${accent}08`; }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = T.border; e.currentTarget.style.background = T.surf; }}
                  >
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: `${accent}12`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: accent }}>
                      {template.icon}
                    </div>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{template.name}</div>
                      <div style={{ fontSize: 11, color: T.muted, marginTop: 2 }}>{template.desc}</div>
                    </div>
                  </motion.button>
                ))}
              </div>

              {/* AI prompt */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: T.surf, border: `1px solid ${T.border}`, borderRadius: 16, padding: 8, transition: 'border-color 0.2s' }}>
                <Sparkles size={20} color={accent} style={{ flexShrink: 0, marginLeft: 8 }} />
                <input
                  value={aiPrompt}
                  onChange={(e) => setAiPrompt(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleAiGenerate(); }}
                  placeholder="O describe que quieres construir..."
                  style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', color: T.text, fontSize: 14, padding: '8px 4px', fontFamily: 'inherit' }}
                />
                <button
                  onClick={handleAiGenerate}
                  disabled={isGenerating || !aiPrompt.trim()}
                  style={{
                    padding: 10, borderRadius: 12, border: 'none', cursor: isGenerating || !aiPrompt.trim() ? 'not-allowed' : 'pointer',
                    background: `${accent}20`, color: accent, display: 'flex', alignItems: 'center', justifyContent: 'center',
                    opacity: isGenerating || !aiPrompt.trim() ? 0.3 : 1, flexShrink: 0, transition: 'all 0.2s',
                  }}
                >
                  {isGenerating ? <Loader2 size={20} style={{ animation: 'nexa-spin 1s linear infinite' }} /> : <Zap size={20} />}
                </button>
              </div>
            </motion.div>
          </div>
        </div>
      </motion.div>
    );
  }

  // ─── Main Canvas Editor ───
  return (
    <motion.div
      ref={containerRef}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{ position: 'fixed', inset: 0, zIndex: 50, display: 'flex', flexDirection: 'column', background: T.bg, color: T.text, fontFamily: "'Inter', sans-serif" }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', height: 48, borderBottom: `1px solid ${T.border}`, background: `${T.surf}CC`, backdropFilter: 'blur(10px)', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, background: `linear-gradient(135deg, ${accent}, ${accent}88)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Code2 size={14} color="#fff" />
          </div>
          <span style={{ fontSize: 14, fontWeight: 700, color: accent }}>Canvas</span>

          {/* File tabs */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 2, marginLeft: 8 }}>
            {files.map((file) => {
              const config = LANG_CONFIG[file.language];
              const isActive = activeFile === file.name;
              return (
                <button
                  key={file.name}
                  onClick={() => setActiveFile(file.name)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px',
                    borderRadius: 6, fontSize: 12, fontWeight: 500,
                    background: isActive ? `${accent}15` : 'transparent',
                    color: isActive ? accent : T.muted,
                    border: 'none', cursor: 'pointer', transition: 'all 0.15s',
                    fontFamily: 'inherit',
                  }}
                  onMouseEnter={(e) => { if (!isActive) { e.currentTarget.style.color = T.text; e.currentTarget.style.background = `${T.surf}80`; } }}
                  onMouseLeave={(e) => { if (!isActive) { e.currentTarget.style.color = T.muted; e.currentTarget.style.background = 'transparent'; } }}
                >
                  <div style={{ width: 6, height: 6, borderRadius: '50%', background: config?.color || '#888' }} />
                  {file.name}
                  {file.modified && <div style={{ width: 6, height: 6, borderRadius: '50%', background: accent }} />}
                </button>
              );
            })}

            {/* Add file button */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setShowNewFileMenu(!showNewFileMenu)}
                style={{ padding: 4, borderRadius: 6, border: 'none', background: 'transparent', color: T.muted, cursor: 'pointer', display: 'flex', marginLeft: 4 }}
              >
                <ChevronDown size={14} />
              </button>
              <AnimatePresence>
                {showNewFileMenu && (
                  <>
                    <div style={{ position: 'fixed', inset: 0, zIndex: 40 }} onClick={() => setShowNewFileMenu(false)} />
                    <motion.div
                      initial={{ opacity: 0, y: -5 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -5 }}
                      style={{
                        position: 'absolute', top: '100%', left: 0, marginTop: 4, width: 176,
                        background: T.surf, border: `1px solid ${T.border}`, borderRadius: 12,
                        boxShadow: '0 10px 40px rgba(0,0,0,0.4)', zIndex: 50, overflow: 'hidden', padding: 4,
                      }}
                    >
                      {[
                        { name: 'style.css', lang: 'css' as CanvasFileType },
                        { name: 'script.js', lang: 'javascript' as CanvasFileType },
                        { name: 'app.ts', lang: 'typescript' as CanvasFileType },
                        { name: 'data.json', lang: 'json' as CanvasFileType },
                      ].map((item) => (
                        <button
                          key={item.name}
                          onClick={() => handleAddFile(item.name, item.lang)}
                          style={{
                            width: '100%', display: 'flex', alignItems: 'center', gap: 8,
                            padding: '8px 12px', fontSize: 12, color: T.muted, background: 'none',
                            border: 'none', cursor: 'pointer', textAlign: 'left', borderRadius: 6,
                            fontFamily: 'inherit', transition: 'all 0.15s',
                          }}
                          onMouseEnter={(e) => { e.currentTarget.style.color = T.text; e.currentTarget.style.background = `${T.surf}80`; }}
                          onMouseLeave={(e) => { e.currentTarget.style.color = T.muted; e.currentTarget.style.background = 'none'; }}
                        >
                          <div style={{ width: 8, height: 8, borderRadius: '50%', background: LANG_CONFIG[item.lang]?.color }} />
                          {item.name}
                        </button>
                      ))}
                    </motion.div>
                  </>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          {/* Split mode toggles */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 2, background: `${T.surf}50`, borderRadius: 8, padding: 2, marginRight: 8 }}>
            <button onClick={() => setSplitMode('code')} style={iconBtnStyle(splitMode === 'code')} title="Solo codigo">
              <Code2 size={14} />
            </button>
            <button onClick={() => setSplitMode('split')} style={iconBtnStyle(splitMode === 'split')} title="Vista dividida">
              <Columns2 size={14} />
            </button>
            <button onClick={() => setSplitMode('preview')} style={iconBtnStyle(splitMode === 'preview')} title="Solo preview">
              <Eye size={14} />
            </button>
          </div>

          {/* Actions */}
          <button onClick={handleCopyCode} style={iconBtnStyle()} title="Copiar codigo">
            {copied ? <Check size={16} color="#22c55e" /> : <Copy size={16} />}
          </button>
          <button onClick={handleDownload} style={iconBtnStyle()} title="Descargar">
            <Download size={16} />
          </button>
          <button onClick={() => setPreviewKey((k) => k + 1)} style={iconBtnStyle()} title="Refrescar preview">
            <RotateCcw size={16} />
          </button>
          <button onClick={toggleFullscreen} style={iconBtnStyle()} title="Pantalla completa">
            {isFullscreen ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
          </button>
          <button onClick={onClose} style={iconBtnStyle()} title="Cerrar">
            <X size={16} />
          </button>
        </div>
      </div>

      {/* ─── Main content - Split View ─── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Code Editor */}
        {splitMode !== 'preview' && (
          <div style={{ width: splitMode === 'split' ? '50%' : '100%', display: 'flex', flexDirection: 'column', minWidth: 0, borderRight: `1px solid ${T.border}50` }}>
            {/* Editor header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 12px', background: `${T.surf}50`, borderBottom: `1px solid ${T.border}50` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {currentFile && LANG_CONFIG[currentFile.language] && (
                  <>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: LANG_CONFIG[currentFile.language].color }} />
                    <span style={{ fontSize: 12, fontWeight: 500, color: T.muted }}>{currentFile.name}</span>
                    <span style={{ fontSize: 10, color: `${T.muted}80` }}>{LANG_CONFIG[currentFile.language].label}</span>
                  </>
                )}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <button
                  onClick={() => setShowAiBar(!showAiBar)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px',
                    borderRadius: 6, fontSize: 12, fontWeight: 500, border: 'none',
                    background: showAiBar ? `${accent}20` : 'transparent',
                    color: showAiBar ? accent : T.muted, cursor: 'pointer', transition: 'all 0.15s',
                    fontFamily: 'inherit',
                  }}
                >
                  <Sparkles size={12} />
                  AI Edit
                </button>
              </div>
            </div>

            {/* AI Edit Bar */}
            <AnimatePresence>
              {showAiBar && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  style={{ overflow: 'hidden', borderBottom: `1px solid ${T.border}50` }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', background: `${accent}08` }}>
                    <Sparkles size={16} color={accent} style={{ flexShrink: 0 }} />
                    <input
                      value={aiPrompt}
                      onChange={(e) => setAiPrompt(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') handleAiGenerate(); }}
                      placeholder="Dile a la IA que cambiar o crear..."
                      style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', color: T.text, fontSize: 14, fontFamily: 'inherit' }}
                    />
                    <button
                      onClick={handleAiGenerate}
                      disabled={isGenerating || !aiPrompt.trim()}
                      style={{
                        padding: '4px 12px', borderRadius: 8, border: 'none',
                        background: `${accent}20`, color: accent, fontSize: 12, fontWeight: 600,
                        cursor: isGenerating || !aiPrompt.trim() ? 'not-allowed' : 'pointer',
                        opacity: isGenerating || !aiPrompt.trim() ? 0.3 : 1,
                        fontFamily: 'inherit', transition: 'all 0.2s',
                      }}
                    >
                      {isGenerating ? <Loader2 size={14} style={{ animation: 'nexa-spin 1s linear infinite' }} /> : 'Apply'}
                    </button>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Code textarea */}
            <div style={{ flex: 1, overflow: 'auto', position: 'relative' }}>
              <textarea
                ref={textareaRef}
                value={currentFile?.content || ''}
                onChange={(e) => handleCodeChange(e.target.value)}
                spellCheck={false}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="off"
                style={{
                  width: '100%', height: '100%', background: '#0d1117',
                  color: '#e6edf3', fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                  fontSize: 13, lineHeight: 1.6, padding: 16, resize: 'none',
                  outline: 'none', border: 'none', whiteSpace: 'pre', overflowX: 'auto',
                  tabSize: 2,
                }}
              />

              {/* Line numbers overlay effect */}
              <div style={{ position: 'absolute', top: 0, left: 0, width: 40, height: '100%', background: `${T.surf}30`, pointerEvents: 'none', borderRight: `1px solid ${T.border}20` }} />
            </div>
          </div>
        )}

        {/* Preview */}
        {splitMode !== 'code' && (
          <div style={{ width: splitMode === 'split' ? '50%' : '100%', display: 'flex', flexDirection: 'column', minWidth: 0 }}>
            {/* Preview header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 12px', background: `${T.surf}50`, borderBottom: `1px solid ${T.border}50` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Eye size={14} color={accent} />
                <span style={{ fontSize: 12, fontWeight: 500, color: T.muted }}>Preview</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#22c55e' }} />
                  <span style={{ fontSize: 10, color: T.muted }}>Live</span>
                </div>
              </div>
            </div>

            {/* Preview iframe */}
            <div style={{ flex: 1, background: '#fff', position: 'relative' }}>
              <iframe
                ref={iframeRef}
                key={previewKey}
                srcDoc={getPreviewContent()}
                style={{ width: '100%', height: '100%', border: 0 }}
                title="Live Preview"
                sandbox="allow-scripts allow-same-origin allow-modals allow-forms allow-popups"
              />

              {/* Generating overlay */}
              {isGenerating && (
                <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 20px', borderRadius: 16, background: T.surf, border: `1px solid ${T.border}`, boxShadow: '0 10px 40px rgba(0,0,0,0.4)' }}>
                    <Loader2 size={20} color={accent} style={{ animation: 'nexa-spin 1s linear infinite' }} />
                    <span style={{ fontSize: 14, fontWeight: 500, color: T.text }}>Generando...</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Bottom AI bar */}
      <div style={{ borderTop: `1px solid ${T.border}`, padding: '8px 12px', background: `${T.surf}CC`, backdropFilter: 'blur(10px)', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, maxWidth: 720, margin: '0 auto' }}>
          <Sparkles size={16} color={accent} style={{ flexShrink: 0 }} />
          <input
            value={aiPrompt}
            onChange={(e) => setAiPrompt(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleAiGenerate(); }}
            placeholder="Pide a la IA editar, crear o arreglar algo..."
            style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', color: T.text, fontSize: 14, fontFamily: 'inherit' }}
          />
          <button
            onClick={handleAiGenerate}
            disabled={isGenerating || !aiPrompt.trim()}
            style={{
              padding: '6px 16px', borderRadius: 12, border: 'none',
              background: `${accent}20`, color: accent, fontSize: 12, fontWeight: 600,
              cursor: isGenerating || !aiPrompt.trim() ? 'not-allowed' : 'pointer',
              opacity: isGenerating || !aiPrompt.trim() ? 0.3 : 1,
              display: 'flex', alignItems: 'center', gap: 6,
              fontFamily: 'inherit', transition: 'all 0.2s',
            }}
          >
            {isGenerating ? <Loader2 size={14} style={{ animation: 'nexa-spin 1s linear infinite' }} /> : <Zap size={14} />}
            Generar
          </button>
        </div>
      </div>

      {/* Spinner animation */}
      <style>{`
        @keyframes nexa-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
      `}</style>
    </motion.div>
  );
}
