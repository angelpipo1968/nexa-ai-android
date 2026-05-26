'use client';

import React, { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X,
  Play,
  Code2,
  Eye,
  Download,
  Copy,
  Check,
  Loader2,
  Sparkles,
  SplitSquareHorizontal,
  Columns2,
  Square,
  RotateCcw,
  Maximize2,
  Minimize2,
  FileCode,
  FileText,
  Palette,
  Type,
  AlignLeft,
  Hash,
  Terminal,
  ChevronDown,
  Zap,
  MessageSquare,
  Send,
  Moon,
  Sun,
  Monitor,
} from 'lucide-react';
import { toast } from 'sonner';

interface CanvasModeProps {
  onClose: () => void;
  onSendMessage?: (text: string) => Promise<void>;
  theme?: string;
}

type SplitMode = 'code' | 'preview' | 'split';
type FileType = 'html' | 'css' | 'javascript' | 'typescript' | 'json' | 'markdown' | 'python';

interface CanvasFile {
  name: string;
  content: string;
  language: FileType;
  modified: boolean;
}

const LANG_CONFIG: Record<FileType, { color: string; icon: React.ReactNode; label: string }> = {
  html: { color: '#E34F26', icon: <FileCode className="w-3.5 h-3.5" />, label: 'HTML' },
  css: { color: '#1572B6', icon: <Palette className="w-3.5 h-3.5" />, label: 'CSS' },
  javascript: { color: '#F7DF1E', icon: <Terminal className="w-3.5 h-3.5" />, label: 'JS' },
  typescript: { color: '#3178C6', icon: <Terminal className="w-3.5 h-3.5" />, label: 'TS' },
  json: { color: '#8B5CF6', icon: <Hash className="w-3.5 h-3.5" />, label: 'JSON' },
  markdown: { color: '#06B6D4', icon: <AlignLeft className="w-3.5 h-3.5" />, label: 'MD' },
  python: { color: '#3776AB', icon: <Terminal className="w-3.5 h-3.5" />, label: 'PY' },
};

const TEMPLATES: { name: string; desc: string; icon: React.ReactNode; files: CanvasFile[] }[] = [
  {
    name: 'Landing Page',
    desc: 'Modern landing page with animations',
    icon: <Sparkles className="w-5 h-5" />,
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
    desc: 'Functional calculator app',
    icon: <Hash className="w-5 h-5" />,
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
    desc: 'Start from scratch',
    icon: <FileText className="w-5 h-5" />,
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

export function CanvasMode({ onClose, onSendMessage, theme = 'dark' }: CanvasModeProps) {
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

  // Combine all files into a single HTML document for preview
  const getPreviewContent = useCallback(() => {
    const htmlFile = files.find((f) => f.name.endsWith('.html'));
    if (!htmlFile) {
      // If no HTML file, create a basic one
      const cssFiles = files.filter((f) => f.name.endsWith('.css'));
      const jsFiles = files.filter((f) => f.name.endsWith('.js') || f.name.endsWith('.ts'));

      let html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NEXA Canvas</title>`;
      cssFiles.forEach((f) => {
        html += `\n  <style>\n${f.content}\n  </style>`;
      });
      html += `\n</head>\n<body>`;
      html += `\n<div id="app"></div>`;
      jsFiles.forEach((f) => {
        html += `\n  <script>\n${f.content}\n  </script>`;
      });
      html += `\n</body>\n</html>`;
      return html;
    }

    let content = htmlFile.content;

    // Inject CSS files
    const cssFiles = files.filter((f) => f.name.endsWith('.css'));
    cssFiles.forEach((f) => {
      if (!content.includes(f.content)) {
        content = content.replace('</head>', `  <style>\n${f.content}\n  </style>\n</head>`);
      }
    });

    // Inject JS files
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
    const timer = setTimeout(() => {
      setPreviewKey((k) => k + 1);
    }, 300);
    return () => clearTimeout(timer);
  }, [files]);

  const handleCodeChange = (value: string) => {
    setFiles((prev) =>
      prev.map((f) => (f.name === activeFile ? { ...f, content: value, modified: true } : f))
    );
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
          messages: [
            {
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
            },
          ],
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
                  // Live update the code as it streams in
                  setFiles((prev) => {
                    const existing = prev.find((f) => f.name === 'index.html');
                    if (existing) {
                      return prev.map((f) =>
                        f.name === 'index.html' ? { ...f, content: fullText, modified: true } : f
                      );
                    }
                    return [
                      { name: 'index.html', language: 'html', content: fullText, modified: true },
                      ...prev,
                    ];
                  });
                }
              } catch {
                // skip
              }
            }
          }
        }
      }

      // Clean up: remove markdown code blocks if AI added them
      let cleaned = fullText.trim();
      if (cleaned.startsWith('```html')) {
        cleaned = cleaned.slice(7);
      } else if (cleaned.startsWith('```')) {
        cleaned = cleaned.slice(3);
      }
      if (cleaned.endsWith('```')) {
        cleaned = cleaned.slice(0, -3);
      }
      cleaned = cleaned.trim();

      setFiles((prev) =>
        prev.map((f) => (f.name === 'index.html' ? { ...f, content: cleaned, modified: false } : f))
      );
      setAiPrompt('');
      toast.success('Code generated!');
    } catch (error: any) {
      toast.error('Generation failed. Try again.');
    } finally {
      setIsGenerating(false);
    }
  };

  const handleCopyCode = async () => {
    if (!currentFile) return;
    await navigator.clipboard.writeText(currentFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    toast.success('Code copied!');
  };

  const handleDownload = () => {
    const content = getPreviewContent();
    const blob = new Blob([content], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'project.html';
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Downloaded!');
  };

  const handleAddFile = (name: string, language: FileType) => {
    if (files.find((f) => f.name === name)) {
      toast.error('File already exists');
      return;
    }
    setFiles((prev) => [...prev, { name, content: '', language, modified: false }]);
    setActiveFile(name);
    setShowNewFileMenu(false);
  };

  const handleDeleteFile = (name: string) => {
    if (files.length <= 1) {
      toast.error('Cannot delete the last file');
      return;
    }
    setFiles((prev) => prev.filter((f) => f.name !== name));
    if (activeFile === name) {
      setActiveFile(files.find((f) => f.name !== name)?.name || '');
    }
  };

  const toggleFullscreen = () => {
    if (!isFullscreen) {
      containerRef.current?.requestFullscreen?.();
    } else {
      document.exitFullscreen?.();
    }
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

  // Template selection screen
  if (showTemplates && files.length === 0) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 z-50 flex flex-col bg-background"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 h-14 border-b border-border/50 glass shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Code2 className="w-4 h-4 text-white" />
            </div>
            <div>
              <h2 className="text-sm font-semibold gradient-text">NEXA Canvas</h2>
              <p className="text-[10px] text-muted-foreground">Code + Live Preview</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-secondary/50 text-muted-foreground hover:text-foreground transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Templates */}
        <div className="flex-1 flex items-center justify-center p-6">
          <div className="max-w-lg w-full text-center">
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
              <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center relative">
                <SplitSquareHorizontal className="w-10 h-10 text-primary" />
                <div className="absolute inset-0 blur-xl bg-primary/20 rounded-2xl" />
              </div>
              <h3 className="text-2xl font-bold text-foreground mb-2">
                <span className="gradient-text">NEXA Canvas</span>
              </h3>
              <p className="text-muted-foreground text-sm mb-8">
                Write code with AI and see the result in real-time. Side by side.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-8">
                {TEMPLATES.map((template, i) => (
                  <motion.button
                    key={i}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 + i * 0.08 }}
                    onClick={() => handleTemplateSelect(template)}
                    className="flex flex-col items-center gap-3 p-5 rounded-2xl bg-card border border-border/50 hover:border-primary/30 hover:bg-primary/5 transition-all group"
                  >
                    <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center text-primary group-hover:scale-110 transition-transform">
                      {template.icon}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-foreground">{template.name}</p>
                      <p className="text-[11px] text-muted-foreground mt-0.5">{template.desc}</p>
                    </div>
                  </motion.button>
                ))}
              </div>

              {/* AI prompt */}
              <div className="relative">
                <div className="flex items-center gap-2 bg-card border border-border/50 rounded-2xl p-2 focus-within:border-primary/40 transition-colors">
                  <Sparkles className="w-5 h-5 text-primary shrink-0 ml-2" />
                  <input
                    value={aiPrompt}
                    onChange={(e) => setAiPrompt(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleAiGenerate();
                    }}
                    placeholder="Or describe what you want to build..."
                    className="flex-1 bg-transparent py-2 px-1 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
                  />
                  <button
                    onClick={handleAiGenerate}
                    disabled={isGenerating || !aiPrompt.trim()}
                    className="p-2.5 rounded-xl bg-primary/20 text-primary hover:bg-primary/30 transition-all disabled:opacity-30 disabled:cursor-not-allowed shrink-0"
                  >
                    {isGenerating ? <Loader2 className="w-5 h-5 animate-spin" /> : <Zap className="w-5 h-5" />}
                  </button>
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </motion.div>
    );
  }

  // Main Canvas Editor
  return (
    <motion.div
      ref={containerRef}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex flex-col bg-background"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 h-12 border-b border-border/50 glass shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center">
            <Code2 className="w-3.5 h-3.5 text-white" />
          </div>
          <span className="text-sm font-semibold gradient-text">Canvas</span>

          {/* File tabs */}
          <div className="flex items-center gap-0.5 ml-2">
            {files.map((file) => {
              const config = LANG_CONFIG[file.language];
              return (
                <button
                  key={file.name}
                  onClick={() => setActiveFile(file.name)}
                  className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium transition-all ${
                    activeFile === file.name
                      ? 'bg-primary/15 text-primary'
                      : 'text-muted-foreground hover:text-foreground hover:bg-secondary/30'
                  }`}
                >
                  <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: config?.color || '#888' }} />
                  {file.name}
                  {file.modified && (
                    <div className="w-1.5 h-1.5 rounded-full bg-primary" />
                  )}
                </button>
              );
            })}

            {/* Add file button */}
            <div className="relative">
              <button
                onClick={() => setShowNewFileMenu(!showNewFileMenu)}
                className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all ml-1"
              >
                <ChevronDown className="w-3.5 h-3.5" />
              </button>
              <AnimatePresence>
                {showNewFileMenu && (
                  <>
                    <div className="fixed inset-0 z-40" onClick={() => setShowNewFileMenu(false)} />
                    <motion.div
                      initial={{ opacity: 0, y: -5 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -5 }}
                      className="absolute top-full left-0 mt-1 w-44 bg-card border border-border/50 rounded-xl shadow-xl z-50 overflow-hidden py-1"
                    >
                      {[
                        { name: 'style.css', lang: 'css' as FileType },
                        { name: 'script.js', lang: 'javascript' as FileType },
                        { name: 'app.ts', lang: 'typescript' as FileType },
                        { name: 'data.json', lang: 'json' as FileType },
                      ].map((item) => (
                        <button
                          key={item.name}
                          onClick={() => handleAddFile(item.name, item.lang)}
                          className="w-full flex items-center gap-2 px-3 py-2 text-xs text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
                        >
                          <div className="w-2 h-2 rounded-full" style={{ backgroundColor: LANG_CONFIG[item.lang]?.color }} />
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

        <div className="flex items-center gap-1">
          {/* Split mode toggles */}
          <div className="flex items-center gap-0.5 bg-secondary/30 rounded-lg p-0.5 mr-2">
            <button
              onClick={() => setSplitMode('code')}
              className={`p-1.5 rounded-md transition-all ${
                splitMode === 'code' ? 'bg-primary/20 text-primary' : 'text-muted-foreground hover:text-foreground'
              }`}
              title="Code only"
            >
              <Code2 className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setSplitMode('split')}
              className={`p-1.5 rounded-md transition-all ${
                splitMode === 'split' ? 'bg-primary/20 text-primary' : 'text-muted-foreground hover:text-foreground'
              }`}
              title="Split view"
            >
              <Columns2 className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setSplitMode('preview')}
              className={`p-1.5 rounded-md transition-all ${
                splitMode === 'preview' ? 'bg-primary/20 text-primary' : 'text-muted-foreground hover:text-foreground'
              }`}
              title="Preview only"
            >
              <Eye className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* Actions */}
          <button
            onClick={handleCopyCode}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
            title="Copy code"
          >
            {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
          </button>
          <button
            onClick={handleDownload}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
            title="Download"
          >
            <Download className="w-4 h-4" />
          </button>
          <button
            onClick={() => setPreviewKey((k) => k + 1)}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
            title="Refresh preview"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            onClick={toggleFullscreen}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
            title="Fullscreen"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/30 transition-all"
            title="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main content - Split View */}
      <div className="flex-1 flex overflow-hidden">
        {/* Code Editor */}
        {splitMode !== 'preview' && (
          <div className={`${splitMode === 'split' ? 'w-1/2' : 'w-full'} flex flex-col min-w-0 border-r border-border/30`}>
            {/* Editor header */}
            <div className="flex items-center justify-between px-3 py-1.5 bg-card/50 border-b border-border/30">
              <div className="flex items-center gap-2">
                {currentFile && LANG_CONFIG[currentFile.language] && (
                  <>
                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: LANG_CONFIG[currentFile.language].color }} />
                    <span className="text-xs font-medium text-muted-foreground">{currentFile.name}</span>
                    <span className="text-[10px] text-muted-foreground/50">
                      {LANG_CONFIG[currentFile.language].label}
                    </span>
                  </>
                )}
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setShowAiBar(!showAiBar)}
                  className={`flex items-center gap-1 px-2 py-1 rounded-md text-xs font-medium transition-all ${
                    showAiBar
                      ? 'bg-primary/20 text-primary'
                      : 'text-muted-foreground hover:text-foreground hover:bg-secondary/30'
                  }`}
                >
                  <Sparkles className="w-3 h-3" />
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
                  className="overflow-hidden border-b border-border/30"
                >
                  <div className="flex items-center gap-2 px-3 py-2 bg-primary/5">
                    <Sparkles className="w-4 h-4 text-primary shrink-0" />
                    <input
                      value={aiPrompt}
                      onChange={(e) => setAiPrompt(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') handleAiGenerate();
                      }}
                      placeholder="Tell AI what to change or create..."
                      className="flex-1 bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
                    />
                    <button
                      onClick={handleAiGenerate}
                      disabled={isGenerating || !aiPrompt.trim()}
                      className="px-3 py-1 rounded-lg bg-primary/20 text-primary text-xs font-medium hover:bg-primary/30 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      {isGenerating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : 'Apply'}
                    </button>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Code textarea */}
            <div className="flex-1 overflow-auto relative">
              <textarea
                ref={textareaRef}
                value={currentFile?.content || ''}
                onChange={(e) => handleCodeChange(e.target.value)}
                className="w-full h-full bg-transparent text-foreground font-mono text-[13px] leading-6 p-4 resize-none focus:outline-none whitespace-pre overflow-x-auto"
                spellCheck={false}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="off"
              />

              {/* Line numbers overlay effect */}
              <div className="absolute top-0 left-0 w-10 h-full bg-card/30 pointer-events-none border-r border-border/20" />
            </div>
          </div>
        )}

        {/* Preview */}
        {splitMode !== 'code' && (
          <div className={`${splitMode === 'split' ? 'w-1/2' : 'w-full'} flex flex-col min-w-0`}>
            {/* Preview header */}
            <div className="flex items-center justify-between px-3 py-1.5 bg-card/50 border-b border-border/30">
              <div className="flex items-center gap-2">
                <Eye className="w-3.5 h-3.5 text-primary" />
                <span className="text-xs font-medium text-muted-foreground">Preview</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center gap-0.5">
                  <div className="w-2 h-2 rounded-full bg-green-400" />
                  <span className="text-[10px] text-muted-foreground">Live</span>
                </div>
              </div>
            </div>

            {/* Preview iframe */}
            <div className="flex-1 bg-white relative">
              <iframe
                ref={iframeRef}
                key={previewKey}
                srcDoc={getPreviewContent()}
                className="w-full h-full border-0"
                title="Live Preview"
                sandbox="allow-scripts allow-same-origin allow-modals allow-forms allow-popups"
              />

              {/* Generating overlay */}
              {isGenerating && (
                <div className="absolute inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center">
                  <div className="flex items-center gap-3 px-5 py-3 rounded-2xl bg-card border border-border/50 shadow-2xl">
                    <Loader2 className="w-5 h-5 animate-spin text-primary" />
                    <span className="text-sm font-medium text-foreground">Generating...</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Bottom AI bar (always visible) */}
      <div className="border-t border-border/50 px-3 py-2 glass shrink-0">
        <div className="flex items-center gap-2 max-w-3xl mx-auto">
          <Sparkles className="w-4 h-4 text-primary shrink-0" />
          <input
            value={aiPrompt}
            onChange={(e) => setAiPrompt(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleAiGenerate();
            }}
            placeholder="Ask AI to edit, create, or fix anything..."
            className="flex-1 bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          <button
            onClick={handleAiGenerate}
            disabled={isGenerating || !aiPrompt.trim()}
            className="px-4 py-1.5 rounded-xl bg-primary/20 text-primary text-xs font-semibold hover:bg-primary/30 transition-all disabled:opacity-30 disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            {isGenerating ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Zap className="w-3.5 h-3.5" />
            )}
            Generate
          </button>
        </div>
      </div>
    </motion.div>
  );
}
