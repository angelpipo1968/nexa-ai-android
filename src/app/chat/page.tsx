'use client'

import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  MessageSquare, Settings, Plus, ChevronLeft, ChevronRight,
  Send, Sparkles, Zap, Bot, User, Clock,
  Hash, Search, Bell, X, ArrowUp,
  FileText, Image as ImageIcon, Code, Globe, ChevronDown,
  Sun, Moon, Trash2, Palette, Headphones, BarChart3, Brain,
  MessageCircle, Copy, CheckCheck, RotateCcw, Activity, Server,
  Cpu, Eye, TrendingUp, Database, Wifi, WifiOff,
  BarChart2, SkipForward, RefreshCw, Menu,
  AlertTriangle, CheckCircle, Info, AlertCircle,
  Volume2, VolumeX, Speaker, AudioWaveform
} from 'lucide-react'
import { useNexaStore, type Message, type RoutingInfo } from '@/lib/store'

// Icon map
const iconMap: Record<string, React.ReactNode> = {
  Hash: <Hash className="w-4 h-4" />,
  Code: <Code className="w-4 h-4" />,
  Palette: <Palette className="w-4 h-4" />,
  Globe: <Globe className="w-4 h-4" />,
  Headphones: <Headphones className="w-4 h-4" />,
  BarChart3: <BarChart3 className="w-4 h-4" />,
  Brain: <Brain className="w-4 h-4" />,
}

const aiModels = [
  { id: 'nexa-pro', name: 'Nexa Pro', desc: 'Advanced model, detailed responses', icon: <Sparkles className="w-3.5 h-3.5" /> },
  { id: 'nexa-fast', name: 'Nexa Fast', desc: 'Fast responses, ideal for chat', icon: <Zap className="w-3.5 h-3.5" /> },
  { id: 'nexa-code', name: 'Nexa Code', desc: 'Specialized in code & tech', icon: <Code className="w-3.5 h-3.5" /> },
  { id: 'qwen', name: 'Qwen 2.5', desc: 'Local model via LiteLLM', icon: <Cpu className="w-3.5 h-3.5" /> },
  { id: 'vision', name: 'Vision', desc: 'Image analysis (Ollama)', icon: <ImageIcon className="w-3.5 h-3.5" /> },
]

const quickActions = [
  { label: 'Write code', icon: <Code className="w-4 h-4" />, prompt: 'Help me write code for ' },
  { label: 'Analyze data', icon: <BarChart3 className="w-4 h-4" />, prompt: 'Analyze this data: ' },
  { label: 'Search info', icon: <Globe className="w-4 h-4" />, prompt: 'Search for information about ' },
  { label: 'Fix bug', icon: <Activity className="w-4 h-4" />, prompt: 'I have this bug, help me fix it: ' },
  { label: 'Summarize text', icon: <FileText className="w-4 h-4" />, prompt: 'Summarize the following text: ' },
]

const intentColors: Record<string, string> = {
  coding: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  reasoning: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
  casual: 'bg-sky-500/15 text-sky-400 border-sky-500/30',
  data: 'bg-violet-500/15 text-violet-400 border-violet-500/30',
  image: 'bg-pink-500/15 text-pink-400 border-pink-500/30',
  creative: 'bg-orange-500/15 text-orange-400 border-orange-500/30',
}

const engineColors: Record<string, string> = {
  'nexa-pro': 'bg-purple-500/15 text-purple-400 border-purple-500/30',
  'nexa-fast': 'bg-cyan-500/15 text-cyan-400 border-cyan-500/30',
  'nexa-code': 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  'litellm': 'bg-blue-500/15 text-blue-400 border-blue-500/30',
  'fastapi-local': 'bg-green-500/15 text-green-400 border-green-500/30',
}

function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4)
}

// ═══════════════════════════════════════════
//  TTS Hook — Web Speech API
// ═══════════════════════════════════════════
function useTTS() {
  const store = useNexaStore()

  const speak = useCallback((text: string) => {
    if (!store.ttsEnabled || typeof window === 'undefined' || !window.speechSynthesis) return

    // Cancel any ongoing speech
    window.speechSynthesis.cancel()

    // Clean markdown for speech
    const cleanText = text
      .replace(/```[\s\S]*?```/g, ' code block ')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\*\*([^*]+)\*\*/g, '$1')
      .replace(/\*([^*]+)\*/g, '$1')
      .replace(/#{1,6}\s/g, '')
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/[>\-]\s/g, '')
      .replace(/\n+/g, '. ')

    const utterance = new SpeechSynthesisUtterance(cleanText)
    utterance.rate = 1.0
    utterance.pitch = 1.0
    utterance.volume = 1.0

    // Try to find a good voice
    const voices = window.speechSynthesis.getVoices()
    if (voices.length > 0) {
      // Prefer Spanish voices, then English, then default
      const esVoice = voices.find(v => v.lang.startsWith('es'))
      const enVoice = voices.find(v => v.lang.startsWith('en') && v.name.toLowerCase().includes('google'))
      const defaultVoice = voices.find(v => v.lang.startsWith('en'))
      utterance.voice = esVoice || enVoice || defaultVoice || voices[0]
    }

    utterance.onstart = () => store.setTtsSpeaking(true)
    utterance.onend = () => store.setTtsSpeaking(false)
    utterance.onerror = () => store.setTtsSpeaking(false)

    window.speechSynthesis.speak(utterance)
  }, [store.ttsEnabled])

  const stop = useCallback(() => {
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.cancel()
      store.setTtsSpeaking(false)
    }
  }, [])

  const toggleTts = useCallback(() => {
    if (store.ttsEnabled) {
      stop()
    }
    store.toggleTts()
  }, [store.ttsEnabled, stop])

  return { speak, stop, toggleTts, isEnabled: store.ttsEnabled, isSpeaking: store.ttsSpeaking }
}

// Nexa Logo
function NexaLogo({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="nexaGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#a855f7" />
          <stop offset="100%" stopColor="#06b6d4" />
        </linearGradient>
      </defs>
      <rect width="100" height="100" rx="22" fill="url(#nexaGrad)" />
      <path d="M30 70V30h10l10 20 10-20h10v40h-8V42l-10 20h-4l-10-20v28H30z" fill="white" fillOpacity="0.95" />
    </svg>
  )
}

// VRAM Gauge
function VramGauge({ usage }: { usage: number }) {
  const r = 40
  const circ = 2 * Math.PI * r
  const offset = circ - (usage / 100) * circ
  const color = usage > 85 ? '#ef4444' : usage > 60 ? '#f59e0b' : '#10b981'
  return (
    <div className="relative flex items-center justify-center">
      <svg width={100} height={100} className="-rotate-90">
        <circle cx={50} cy={50} r={r} fill="none" stroke="#1f1f1f" strokeWidth={8} />
        <motion.circle
          cx={50} cy={50} r={r} fill="none" stroke={color} strokeWidth={8}
          strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={offset}
          initial={{ strokeDashoffset: circ }}
          animate={{ strokeDashoffset: offset }}
          transition={{ duration: 1, ease: 'easeOut' }}
        />
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-xl font-bold" style={{ color }}>{usage}%</span>
        <span className="text-[9px] text-gray-500">VRAM</span>
      </div>
    </div>
  )
}

// Confidence Badge
function ConfidenceBadge({ confidence }: { confidence: number }) {
  const pct = Math.round(confidence * 100)
  const color = pct >= 80 ? 'text-emerald-400' : pct >= 50 ? 'text-amber-400' : 'text-red-400'
  const bg = pct >= 80 ? 'bg-emerald-500' : pct >= 50 ? 'bg-amber-500' : 'bg-red-500'
  return (
    <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded border border-gray-700 bg-gray-800/50">
      <span className="w-8 h-1.5 rounded-full overflow-hidden bg-gray-700 inline-block">
        <span className={`h-full rounded-full ${bg} inline-block`} style={{ width: `${pct}%` }} />
      </span>
      <span className={color}>{pct}%</span>
    </span>
  )
}

// Clean agentic traces
function cleanAgentic(raw: string): string {
  if (!raw) return ''
  let c = raw
  c = c.replace(/\[🛠️[^\]]*\]/g, '')
  c = c.replace(/\[✅[^\]]*\]/g, '')
  c = c.replace(/\[❌[^\]]*\]/g, '')
  c = c.replace(/\[Nexa Kernel:[^\]]*\]/g, '')
  c = c.replace(/⚡/g, '')
  c = c.replace(/\n{3,}/g, '\n\n')
  return c.trim()
}

// ═══════════════════════════════════════════
//  Speaking Indicator Animation
// ═══════════════════════════════════════════
function SpeakingIndicator() {
  return (
    <div className="flex items-center gap-1.5 px-2 py-1 rounded-full bg-purple-500/20 border border-purple-500/30">
      <div className="flex gap-0.5 items-end h-3">
        {[1, 2, 3, 4].map(i => (
          <motion.div
            key={i}
            className="w-0.5 bg-purple-400 rounded-full"
            animate={{ height: ['4px', '12px', '4px'] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: i * 0.1 }}
          />
        ))}
      </div>
      <span className="text-[10px] text-purple-300 font-medium">Speaking...</span>
    </div>
  )
}

export default function ChatWorkspace() {
  const store = useNexaStore()
  const tts = useTTS()
  const [inputValue, setInputValue] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [commandQuery, setCommandQuery] = useState('')
  const [mounted, setMounted] = useState(false)
  const [isMobile, setIsMobile] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // Hydration fix
  useEffect(() => setMounted(true), [])

  // Detect mobile
  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 768)
    check()
    window.addEventListener('resize', check)
    return () => window.removeEventListener('resize', check)
  }, [])

  // Close mobile sidebar on navigation
  useEffect(() => {
    if (isMobile) store.setMobileSidebarOpen(false)
  }, [store.activeChannel, store.activeConversation])

  if (!mounted) return null

  const activeChannelData = store.channels.find(c => c.id === store.activeChannel)
  const activeConv = store.conversations.find(c => c.id === store.activeConversation)
  const messages = activeConv?.messages || []
  const currentModel = aiModels.find(m => m.id === store.selectedModel) || aiModels[0]

  // Simulate VRAM
  useEffect(() => {
    const simulate = () => {
      const base = store.datacenterStatus === 'online' ? 42 : 8
      const jitter = Math.random() * 15 - 5
      store.setVramUsage(Math.max(0, Math.min(100, Math.round(base + jitter))))
    }
    simulate()
    const interval = setInterval(simulate, 8000)
    return () => clearInterval(interval)
  }, [store.datacenterStatus])

  // Check Datacenter
  useEffect(() => {
    const checkDc = async () => {
      store.setDatacenterStatus('checking')
      const start = performance.now()
      try {
        const res = await fetch('/api/chat', { method: 'GET' })
        const latency = Math.round(performance.now() - start)
        const data = await res.json()
        const isOnline = data.datacenter === 'online'
        store.setDatacenterStatus(isOnline ? 'online' : 'offline')
        store.setPingLatency(isOnline ? latency : null)
        store.setLastPingTime(Date.now())
      } catch {
        store.setDatacenterStatus('offline')
        store.setPingLatency(null)
        store.setLastPingTime(Date.now())
      }
    }
    checkDc()
    const interval = setInterval(checkDc, 30000)
    return () => clearInterval(interval)
  }, [])

  // Apply theme
  useEffect(() => {
    document.documentElement.className = store.theme
  }, [store.theme])

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        store.setShowSearch(true)
        setCommandQuery('')
      }
      if (e.key === 'Escape') {
        store.setShowSearch(false)
        store.setShowDashboard(false)
        store.setShowSettings(false)
        store.setShowNotifications(false)
        store.setMobileSidebarOpen(false)
        setCommandQuery('')
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [store])

  // Scroll to bottom
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, isLoading, scrollToBottom])

  // Send message
  const handleSend = useCallback(async () => {
    if (!inputValue.trim() || isLoading) return
    let convId = store.activeConversation
    if (!convId) {
      convId = store.createConversation(store.activeChannel, store.selectedModel)
    }
    const userTokens = estimateTokens(inputValue.trim())
    const userMsg: Message = {
      id: Date.now().toString(), role: 'user', content: inputValue.trim(),
      timestamp: Date.now(), tokens: userTokens
    }
    store.addMessage(convId, userMsg)
    store.markChannelRead(store.activeChannel)
    store.incrementMessages()
    store.incrementTokens(userTokens)
    const msgText = inputValue.trim()
    setInputValue('')
    if (inputRef.current) inputRef.current.style.height = 'auto'
    setIsLoading(true)
    const sendStart = performance.now()
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: msgText,
          model: currentModel.id,
          history: store.conversations.find(c => c.id === convId)?.messages || [],
          skipJudge: store.skipJudge
        })
      })
      const data = await res.json()
      if (data.error) throw new Error(data.error)
      const responseTime = Math.round(performance.now() - sendStart)
      if (data.routing) {
        store.setLastRouting(data.routing)
        store.trackEngine(data.routing.engine || 'unknown')
      }
      const aiTokens = estimateTokens(data.response || '')
      const cleanedResponse = cleanAgentic(data.response || '')
      const aiMsg: Message = {
        id: Date.now().toString() + '-ai', role: 'assistant',
        content: cleanedResponse,
        timestamp: Date.now(), model: data.model, routing: data.routing,
        judge: data.judge, datacenter: data.datacenter,
        tokens: aiTokens, responseTime
      }
      store.addMessage(convId!, aiMsg)
      store.incrementMessages()
      store.incrementTokens(aiTokens)
      store.addResponseTime(responseTime)
      if (data.datacenter) {
        store.addNotification('Datacenter Response', `Via local RTX 3090 in ${responseTime}ms`, 'success')
      }
      // Auto-speak if TTS enabled
      if (tts.isEnabled && store.ttsAutoSpeak && cleanedResponse) {
        tts.speak(cleanedResponse)
      }
    } catch (err: any) {
      const errorMsg: Message = {
        id: Date.now().toString() + '-err', role: 'assistant',
        content: 'Error: ' + (err.message || 'Could not connect to AI'),
        timestamp: Date.now(), model: 'Error'
      }
      store.addMessage(convId!, errorMsg)
      store.addNotification('AI Error', err.message || 'Connection failed', 'error')
    } finally {
      setIsLoading(false)
    }
  }, [inputValue, isLoading, store, currentModel, tts])

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() }
  }, [handleSend])

  const handleQuickAction = useCallback((prompt: string) => {
    setInputValue(prompt)
    inputRef.current?.focus()
  }, [])

  const copyToClipboard = useCallback((text: string, id: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 2000)
  }, [])

  const regenerateResponse = useCallback(async () => {
    if (!activeConv || activeConv.messages.length < 2 || isLoading) return
    const lastUserMsg = [...activeConv.messages].reverse().find(m => m.role === 'user')
    if (!lastUserMsg) return
    const convId = activeConv.id
    const msgs = activeConv.messages
    let updatedConv = activeConv
    if (msgs[msgs.length - 1]?.role === 'assistant') {
      updatedConv = { ...activeConv, messages: msgs.slice(0, -1) }
      store.setState({ conversations: store.conversations.map(c => c.id === convId ? updatedConv : c) })
    }
    setInputValue(lastUserMsg.content)
    setIsLoading(true)
    const sendStart = performance.now()
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: lastUserMsg.content,
          model: currentModel.id,
          history: updatedConv.messages.slice(-10),
          skipJudge: store.skipJudge
        })
      })
      const data = await res.json()
      if (data.error) throw new Error(data.error)
      const responseTime = Math.round(performance.now() - sendStart)
      if (data.routing) { store.setLastRouting(data.routing); store.trackEngine(data.routing.engine || 'unknown') }
      const aiTokens = estimateTokens(data.response || '')
      const cleanedResponse = cleanAgentic(data.response || '')
      const aiMsg: Message = {
        id: Date.now().toString() + '-ai', role: 'assistant',
        content: cleanedResponse,
        timestamp: Date.now(), model: data.model, routing: data.routing,
        judge: data.judge, datacenter: data.datacenter,
        tokens: aiTokens, responseTime
      }
      store.addMessage(convId, aiMsg)
      store.incrementTokens(aiTokens)
      store.addResponseTime(responseTime)
      // Auto-speak if TTS enabled
      if (tts.isEnabled && store.ttsAutoSpeak && cleanedResponse) {
        tts.speak(cleanedResponse)
      }
    } catch {} finally { setIsLoading(false); setInputValue('') }
  }, [activeConv, isLoading, currentModel, store, tts])

  const formatTime = useCallback((ts: number) => {
    return new Date(ts).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
  }, [])

  const formatUptime = useCallback((startMs: number) => {
    const diff = Date.now() - startMs
    const mins = Math.floor(diff / 60000)
    const hrs = Math.floor(mins / 60)
    if (hrs > 0) return `${hrs}h ${mins % 60}m`
    return `${mins}m`
  }, [])

  const channelConvs = useMemo(() =>
    store.conversations.filter(c => c.channelId === store.activeChannel),
    [store.conversations, store.activeChannel]
  )

  const avgResponseTime = useMemo(() => {
    if (store.responseTimes.length === 0) return 0
    return Math.round(store.responseTimes.reduce((a, b) => a + b, 0) / store.responseTimes.length)
  }, [store.responseTimes])

  const commandItems = useMemo(() => [
    { id: 'new-chat', label: 'New Chat', icon: <Plus className="w-4 h-4" />, action: () => store.setActiveConversation(null) },
    { id: 'dashboard', label: 'Open Dashboard', icon: <BarChart2 className="w-4 h-4" />, action: () => store.setShowDashboard(true) },
    { id: 'toggle-theme', label: 'Toggle Theme', icon: store.theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />, action: () => store.toggleTheme() },
    { id: 'toggle-trace', label: 'Toggle Reasoning Trace', icon: <Eye className="w-4 h-4" />, action: () => store.toggleReasoningTrace() },
    { id: 'toggle-judge', label: `${store.skipJudge ? 'Enable' : 'Disable'} Judge Override`, icon: <SkipForward className="w-4 h-4" />, action: () => store.toggleSkipJudge() },
    { id: 'toggle-tts', label: `${store.ttsEnabled ? 'Disable' : 'Enable'} Hands-Free Voice`, icon: store.ttsEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />, action: () => tts.toggleTts() },
    { id: 'model-pro', label: 'Switch to Nexa Pro', icon: <Sparkles className="w-4 h-4" />, action: () => store.setSelectedModel('nexa-pro') },
    { id: 'model-fast', label: 'Switch to Nexa Fast', icon: <Zap className="w-4 h-4" />, action: () => store.setSelectedModel('nexa-fast') },
    { id: 'model-qwen', label: 'Switch to Qwen Local', icon: <Cpu className="w-4 h-4" />, action: () => store.setSelectedModel('qwen') },
    { id: 'clear-history', label: 'Clear All History', icon: <Trash2 className="w-4 h-4" />, action: () => { store.setState({ conversations: [] }); store.setActiveConversation(null) } },
    { id: 'focus-chat', label: 'Focus Chat Input', icon: <MessageSquare className="w-4 h-4" />, action: () => inputRef.current?.focus() },
    ...store.conversations.slice(0, 10).map(conv => ({
      id: `conv-${conv.id}`, label: conv.title, icon: <MessageCircle className="w-4 h-4" />,
      action: () => { store.setActiveConversation(conv.id); store.setActiveChannel(conv.channelId) }
    })),
  ], [store, tts])

  const filteredCommands = useMemo(() => {
    if (!commandQuery.trim()) return commandItems.slice(0, 12)
    const q = commandQuery.toLowerCase()
    return commandItems.filter(item => item.label.toLowerCase().includes(q)).slice(0, 12)
  }, [commandItems, commandQuery])

  const unreadNotifCount = store.notifications.filter(n => !n.read).length
  const isDark = store.theme === 'dark'

  // ═══════════════════════════════════════════
  //  SIDEBAR CONTENT (shared between desktop & mobile)
  // ═══════════════════════════════════════════
  const sidebarContent = (
    <>
      {/* Sidebar Header */}
      <div className={`flex items-center gap-3 px-4 h-14 border-b ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
        <AnimatePresence mode="wait">
          {(!store.sidebarCollapsed || isMobile) && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex items-center gap-2.5 flex-1 min-w-0">
              <NexaLogo size={28} />
              <span className="font-bold text-lg nexa-gradient">Nexa</span>
              <motion.span
                initial={{ scale: 0 }} animate={{ scale: 1 }}
                className={`ml-1 w-2 h-2 rounded-full ${
                  store.datacenterStatus === 'online' ? 'bg-emerald-500' :
                  store.datacenterStatus === 'offline' ? 'bg-red-500' :
                  'bg-amber-500 animate-pulse'
                }`}
              />
            </motion.div>
          )}
        </AnimatePresence>
        {isMobile && (
          <button onClick={() => store.setMobileSidebarOpen(false)} className="p-2 rounded-lg hover:bg-gray-800">
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* New Chat Button */}
      <div className="px-3 py-3">
        <button
          onClick={() => { store.setActiveConversation(null); if (isMobile) store.setMobileSidebarOpen(false) }}
          className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
            isDark ? 'bg-purple-600/20 text-purple-300 hover:bg-purple-600/30 border border-purple-500/20' : 'bg-purple-100 text-purple-700 hover:bg-purple-200 border border-purple-200'
          }`}
        >
          <Plus className="w-4 h-4" /> New Chat
        </button>
      </div>

      {/* Channels */}
      <div className="flex-1 overflow-y-auto px-2 py-1">
        {store.channels.map(channel => (
          <button
            key={channel.id}
            onClick={() => { store.setActiveChannel(channel.id); if (isMobile) store.setMobileSidebarOpen(false) }}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-all mb-0.5 ${
              store.activeChannel === channel.id
                ? isDark ? 'bg-gray-800 text-white' : 'bg-gray-200 text-gray-900'
                : isDark ? 'text-gray-400 hover:bg-gray-800/50' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {iconMap[channel.icon] || <Hash className="w-4 h-4" />}
            <span className="flex-1 text-left truncate">{channel.name}</span>
            {channel.unread > 0 && (
              <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-medium">{channel.unread}</span>
            )}
          </button>
        ))}

        {/* Conversations */}
        {channelConvs.length > 0 && (
          <>
            <div className={`text-[10px] font-semibold uppercase tracking-wider mt-4 mb-1.5 px-3 ${isDark ? 'text-gray-600' : 'text-gray-400'}`}>Conversations</div>
            {channelConvs.map(conv => (
              <div
                key={conv.id}
                onClick={() => { store.setActiveConversation(conv.id); if (isMobile) store.setMobileSidebarOpen(false) }}
                className={`flex items-center gap-2 w-full rounded-lg px-2.5 py-2 text-sm cursor-pointer transition-all group ${
                  store.activeConversation === conv.id
                    ? isDark ? 'bg-gray-800 text-white' : 'bg-purple-100 text-purple-900'
                    : isDark ? 'text-gray-400 hover:bg-gray-800/30' : 'text-gray-500 hover:bg-gray-100'
                }`}
              >
                <MessageCircle className="w-4 h-4 shrink-0 text-gray-500" />
                <span className="flex-1 truncate">{conv.title}</span>
                <span className="text-[10px] text-gray-500 opacity-60">{conv.messages.length}</span>
                <button
                  className="h-6 w-6 opacity-0 group-hover:opacity-100 shrink-0 hover:text-red-400 transition-all"
                  onClick={(e) => { e.stopPropagation(); store.deleteConversation(conv.id) }}
                >
                  <Trash2 className="w-3 h-3" />
                </button>
              </div>
            ))}
          </>
        )}
      </div>

      {/* Sidebar Bottom */}
      <div className={`px-3 py-2 border-t ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
        <div className="flex items-center justify-between text-[10px] text-gray-500">
          <span>{store.totalMessages} msgs</span>
          <span>{formatUptime(store.uptimeStart)}</span>
        </div>
      </div>

      {/* Sidebar Bottom Actions */}
      <div className={`px-2 py-2 border-t flex flex-col gap-1 ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
        <button
          onClick={() => { store.setShowDashboard(true); if (isMobile) store.setMobileSidebarOpen(false) }}
          className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-all ${
            isDark ? 'text-gray-400 hover:bg-gray-800' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          <BarChart2 className="w-4 h-4" />
          <span>Dashboard</span>
        </button>
        <button
          onClick={() => tts.toggleTts()}
          className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-all ${
            tts.isEnabled
              ? 'bg-purple-500/20 text-purple-300'
              : isDark ? 'text-gray-400 hover:bg-gray-800' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          {tts.isEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          <span>{tts.isEnabled ? 'Voice On' : 'Voice Off'}</span>
        </button>
        <button
          onClick={() => store.toggleTheme()}
          className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-all ${
            isDark ? 'text-gray-400 hover:bg-gray-800' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          <span>{isDark ? 'Light Mode' : 'Dark Mode'}</span>
        </button>
        <button
          onClick={() => { store.setShowSettings(true); if (isMobile) store.setMobileSidebarOpen(false) }}
          className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-all ${
            isDark ? 'text-gray-400 hover:bg-gray-800' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          <Settings className="w-4 h-4" />
          <span>Settings</span>
        </button>
      </div>
    </>
  )

  return (
    <div className={`flex h-screen w-screen overflow-hidden ${isDark ? 'bg-[#0a0a0f] text-gray-100' : 'bg-white text-gray-900'}`}>

      {/* ══════ DESKTOP SIDEBAR ══════ */}
      {!isMobile && (
        <motion.aside
          className={`flex flex-col h-full border-r relative ${isDark ? 'bg-[#0f0f1a] border-gray-800' : 'bg-gray-50 border-gray-200'}`}
          animate={{ width: store.sidebarCollapsed ? 64 : 280 }}
          transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
        >
          {store.sidebarCollapsed ? (
            <div className="flex flex-col h-full">
              <div className="flex justify-center items-center h-14 border-b border-gray-800">
                <NexaLogo size={28} />
                <span className={`w-1.5 h-1.5 rounded-full ml-1 ${
                  store.datacenterStatus === 'online' ? 'bg-emerald-500' :
                  store.datacenterStatus === 'offline' ? 'bg-red-500' : 'bg-amber-500 animate-pulse'
                }`} />
              </div>
              <div className="flex-1 overflow-y-auto py-2 px-1">
                <button onClick={() => store.setActiveConversation(null)} className="w-full flex items-center justify-center p-2.5 rounded-lg hover:bg-gray-800">
                  <Plus className="w-4 h-4 text-purple-400" />
                </button>
                {store.channels.map(ch => (
                  <button key={ch.id} onClick={() => store.setActiveChannel(ch.id)}
                    className={`w-full flex items-center justify-center p-2.5 rounded-lg mb-0.5 ${
                      store.activeChannel === ch.id ? 'bg-gray-800 text-white' : 'text-gray-500 hover:bg-gray-800/50'
                    }`}
                  >
                    {iconMap[ch.icon] || <Hash className="w-4 h-4" />}
                  </button>
                ))}
              </div>
              <div className="px-1 py-2 border-t border-gray-800 flex flex-col gap-1">
                <button onClick={() => store.setShowDashboard(true)} className="w-full flex items-center justify-center p-2.5 rounded-lg text-gray-500 hover:bg-gray-800">
                  <BarChart2 className="w-4 h-4" />
                </button>
                <button onClick={() => tts.toggleTts()} className={`w-full flex items-center justify-center p-2.5 rounded-lg ${tts.isEnabled ? 'bg-purple-500/20 text-purple-400' : 'text-gray-500 hover:bg-gray-800'}`}>
                  {tts.isEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
                </button>
                <button onClick={() => store.toggleTheme()} className="w-full flex items-center justify-center p-2.5 rounded-lg text-gray-500 hover:bg-gray-800">
                  {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                </button>
              </div>
            </div>
          ) : sidebarContent}
          {/* Collapse Toggle */}
          <button
            onClick={() => store.setSidebarCollapsed(!store.sidebarCollapsed)}
            className={`absolute -right-3 top-16 w-6 h-6 rounded-full border flex items-center justify-center z-10 transition-all ${
              isDark ? 'bg-[#0f0f1a] border-gray-700 text-gray-400 hover:text-white' : 'bg-white border-gray-300 text-gray-500 hover:text-gray-900'
            }`}
          >
            {store.sidebarCollapsed ? <ChevronRight className="w-3 h-3" /> : <ChevronLeft className="w-3 h-3" />}
          </button>
        </motion.aside>
      )}

      {/* ══════ MOBILE SIDEBAR (Drawer) ══════ */}
      {isMobile && (
        <AnimatePresence>
          {store.mobileSidebarOpen && (
            <>
              <motion.div
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                className="fixed inset-0 bg-black/60 z-40"
                onClick={() => store.setMobileSidebarOpen(false)}
              />
              <motion.aside
                initial={{ x: -280 }} animate={{ x: 0 }} exit={{ x: -280 }}
                transition={{ type: 'spring', damping: 25, stiffness: 300 }}
                className={`fixed left-0 top-0 bottom-0 w-[280px] z-50 flex flex-col ${
                  isDark ? 'bg-[#0f0f1a]' : 'bg-gray-50'
                }`}
              >
                {sidebarContent}
              </motion.aside>
            </>
          )}
        </AnimatePresence>
      )}

      {/* ══════ MAIN CONTENT ══════ */}
      <main className="flex-1 flex flex-col h-full min-w-0">

        {/* Header */}
        <header className={`flex items-center h-14 px-2 sm:px-4 border-b shrink-0 ${isDark ? 'bg-[#0f0f1a] border-gray-800' : 'bg-gray-50 border-gray-200'}`}>
          {/* Mobile hamburger */}
          {isMobile && (
            <button
              onClick={() => store.setMobileSidebarOpen(true)}
              className={`p-2 mr-1 rounded-lg transition-all ${isDark ? 'hover:bg-gray-800 text-gray-400' : 'hover:bg-gray-100 text-gray-600'}`}
            >
              <Menu className="w-5 h-5" />
            </button>
          )}

          <div className="flex items-center gap-2 flex-1 min-w-0">
            {/* Mobile: show Nexa logo when no sidebar */}
            {isMobile && <NexaLogo size={24} />}
            <span className="font-semibold truncate text-sm sm:text-base">{activeChannelData?.name || 'Chat'}</span>
            <span className="text-[10px] text-gray-500 hidden sm:inline">{messages.length} msgs</span>
          </div>

          <div className="flex items-center gap-1 sm:gap-2">
            {/* TTS Speaking Indicator */}
            {tts.isSpeaking && <SpeakingIndicator />}

            {/* Datacenter Semaphore — hide on very small screens */}
            <div className={`hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border ${
              store.datacenterStatus === 'online'
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                : store.datacenterStatus === 'offline'
                  ? 'bg-red-500/10 text-red-400 border-red-500/20'
                  : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
            }`}>
              {store.datacenterStatus === 'online' ? <Wifi className="w-3 h-3" /> : <WifiOff className="w-3 h-3" />}
              {store.datacenterStatus === 'online' ? 'RTX' : store.datacenterStatus === 'offline' ? 'Cloud' : '...'}
              {store.pingLatency && <span className="text-[9px] opacity-60">{store.pingLatency}ms</span>}
            </div>

            {/* Mobile: simplified datacenter indicator */}
            <div className={`sm:hidden flex items-center justify-center w-7 h-7 rounded-full ${
              store.datacenterStatus === 'online' ? 'bg-emerald-500/20 text-emerald-400' :
              store.datacenterStatus === 'offline' ? 'bg-red-500/20 text-red-400' : 'bg-amber-500/20 text-amber-400'
            }`}>
              {store.datacenterStatus === 'online' ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
            </div>

            {/* TTS Toggle */}
            <button
              onClick={() => tts.toggleTts()}
              className={`p-2 rounded-lg transition-all ${
                tts.isEnabled
                  ? 'bg-purple-500/20 text-purple-400'
                  : isDark ? 'hover:bg-gray-800 text-gray-400' : 'hover:bg-gray-100 text-gray-600'
              }`}
              title={tts.isEnabled ? 'Disable Hands-Free Voice' : 'Enable Hands-Free Voice'}
            >
              {tts.isEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
            </button>

            {/* Skip Judge — hidden on mobile */}
            <button
              onClick={() => store.toggleSkipJudge()}
              className={`hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border transition-all ${
                store.skipJudge
                  ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                  : isDark ? 'bg-gray-800 text-gray-400 border-gray-700' : 'bg-gray-200 text-gray-600 border-gray-300'
              }`}
            >
              <SkipForward className="w-3 h-3" />
              {store.skipJudge ? 'Bypass' : 'Judge'}
            </button>

            {/* Notifications */}
            <button
              onClick={() => store.setShowNotifications(!store.showNotifications)}
              className={`relative p-2 rounded-lg transition-all ${isDark ? 'hover:bg-gray-800 text-gray-400' : 'hover:bg-gray-100 text-gray-600'}`}
            >
              <Bell className="w-4 h-4" />
              {unreadNotifCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 w-4 h-4 rounded-full bg-red-500 text-[9px] text-white flex items-center justify-center">{unreadNotifCount}</span>
              )}
            </button>

            {/* Dashboard */}
            <button
              onClick={() => store.setShowDashboard(true)}
              className={`p-2 rounded-lg transition-all ${isDark ? 'hover:bg-gray-800 text-gray-400' : 'hover:bg-gray-100 text-gray-600'}`}
            >
              <BarChart2 className="w-4 h-4" />
            </button>

            {/* Ctrl+K hint — hidden on mobile */}
            <kbd className={`hidden md:inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono ${isDark ? 'bg-gray-800 text-gray-500' : 'bg-gray-200 text-gray-500'}`}>
              Ctrl+K
            </kbd>
          </div>
        </header>

        {/* Reasoning Trace */}
        <AnimatePresence>
          {store.showReasoningTrace && store.lastRouting && (
            <motion.div
              initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }} className="overflow-hidden"
            >
              <div className={`px-3 sm:px-4 py-2 border-b text-[11px] flex items-center gap-2 sm:gap-3 flex-wrap ${isDark ? 'bg-gray-900/50 border-gray-800' : 'bg-gray-50 border-gray-200'}`}>
                <span className="text-gray-500">Route:</span>
                <span className={`px-1.5 py-0.5 rounded border text-[10px] ${intentColors[store.lastRouting.intent] || 'bg-gray-700 text-gray-300 border-gray-600'}`}>
                  {store.lastRouting.intent}
                </span>
                <span className={`px-1.5 py-0.5 rounded border text-[10px] ${engineColors[store.lastRouting.engine] || 'bg-gray-700 text-gray-300 border-gray-600'}`}>
                  {store.lastRouting.engine}
                </span>
                <ConfidenceBadge confidence={store.lastRouting.confidence} />
                <span className="text-gray-500 truncate max-w-[200px] sm:max-w-xs hidden sm:inline">{store.lastRouting.reasoning}</span>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Chat Area */}
        <div className="flex-1 overflow-y-auto px-3 sm:px-4 py-4 sm:py-6">
          {messages.length === 0 ? (
            /* Welcome Screen */
            <div className="flex flex-col items-center justify-center h-full text-center px-4">
              <motion.div
                initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
                transition={{ duration: 0.5 }}
                className="mb-6"
              >
                <NexaLogo size={isMobile ? 48 : 64} />
              </motion.div>
              <h2 className={`text-xl sm:text-2xl font-bold mb-2 ${isDark ? 'text-white' : 'text-gray-900'}`}>
                Welcome to <span className="nexa-gradient">Nexa</span>
              </h2>
              <p className="text-gray-500 mb-6 sm:mb-8 max-w-md text-sm sm:text-base">Your AI workspace with RTX 3090 power, 37+ tools, and intelligent routing.</p>

              {/* TTS Welcome Notice */}
              {tts.isEnabled && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                  className="mb-6 flex items-center gap-2 px-4 py-2 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-300 text-sm"
                >
                  <Volume2 className="w-4 h-4" />
                  <span>Hands-Free mode is ON — AI responses will be spoken aloud</span>
                </motion.div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 sm:gap-3 max-w-2xl w-full">
                {quickActions.map(action => (
                  <button
                    key={action.label}
                    onClick={() => handleQuickAction(action.prompt)}
                    className={`flex items-center gap-3 px-3 sm:px-4 py-2.5 sm:py-3 rounded-xl text-sm text-left transition-all active:scale-[0.98] ${
                      isDark ? 'bg-gray-800/50 hover:bg-gray-800 border border-gray-700/50' : 'bg-gray-100 hover:bg-gray-200 border border-gray-200'
                    }`}
                  >
                    <span className="text-purple-400">{action.icon}</span>
                    {action.label}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            /* Messages */
            <div className="max-w-3xl mx-auto space-y-3 sm:space-y-4">
              {messages.map(msg => (
                <motion.div
                  key={msg.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.2 }}
                  className={`flex gap-2 sm:gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {msg.role === 'assistant' && (
                    <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg bg-gradient-to-br from-purple-500 to-cyan-500 flex items-center justify-center text-white text-xs sm:text-sm font-bold shrink-0">N</div>
                  )}
                  <div className={`max-w-[85%] sm:max-w-[80%] ${msg.role === 'user' ? 'order-first' : ''}`}>
                    <div className={`px-3 sm:px-4 py-2.5 sm:py-3 rounded-2xl text-sm leading-relaxed ${
                      msg.role === 'user'
                        ? 'bg-purple-600 text-white rounded-br-md'
                        : isDark ? 'bg-gray-800 text-gray-100 rounded-bl-md' : 'bg-gray-100 text-gray-900 rounded-bl-md'
                    }`}>
                      <div className="whitespace-pre-wrap break-words">{msg.content}</div>
                    </div>
                    {/* Message Badges */}
                    {msg.role === 'assistant' && msg.model && (
                      <div className="flex items-center gap-1 sm:gap-1.5 mt-1.5 flex-wrap">
                        {msg.model && <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-800 text-gray-400">{msg.model}</span>}
                        {msg.routing?.intent && <span className={`text-[10px] px-1.5 py-0.5 rounded border ${intentColors[msg.routing.intent] || ''}`}>{msg.routing.intent}</span>}
                        {msg.routing?.engine && <span className={`text-[10px] px-1.5 py-0.5 rounded border ${engineColors[msg.routing.engine] || ''}`}>{msg.routing.engine}</span>}
                        {msg.routing && <ConfidenceBadge confidence={msg.routing.confidence} />}
                        {msg.tokens && <span className="text-[10px] text-gray-500 hidden sm:inline">{msg.tokens} tok</span>}
                        {msg.responseTime && <span className="text-[10px] text-gray-500">{msg.responseTime}ms</span>}
                        {msg.datacenter && <span className="text-[10px] text-emerald-400">GPU</span>}
                      </div>
                    )}
                    {/* Actions */}
                    {msg.role === 'assistant' && (
                      <div className="flex items-center gap-1 mt-1">
                        <button onClick={() => copyToClipboard(msg.content, msg.id)} className="p-1.5 rounded-lg hover:bg-gray-800 text-gray-500 hover:text-gray-300 transition-all active:scale-95">
                          {copiedId === msg.id ? <CheckCheck className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                        </button>
                        <button onClick={regenerateResponse} className="p-1.5 rounded-lg hover:bg-gray-800 text-gray-500 hover:text-gray-300 transition-all active:scale-95">
                          <RotateCcw className="w-3.5 h-3.5" />
                        </button>
                        {/* TTS Speak Button */}
                        <button
                          onClick={() => tts.speak(msg.content)}
                          className={`p-1.5 rounded-lg transition-all active:scale-95 ${
                            tts.isSpeaking
                              ? 'text-purple-400 hover:bg-purple-500/20'
                              : 'text-gray-500 hover:text-gray-300 hover:bg-gray-800'
                          }`}
                          title="Speak this response"
                        >
                          <Volume2 className="w-3.5 h-3.5" />
                        </button>
                        {tts.isSpeaking && (
                          <button onClick={() => tts.stop()} className="p-1.5 rounded-lg text-red-400 hover:bg-red-500/20 transition-all active:scale-95" title="Stop speaking">
                            <VolumeX className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                  {msg.role === 'user' && (
                    <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg bg-gray-600 flex items-center justify-center text-white text-xs sm:text-sm font-bold shrink-0">U</div>
                  )}
                </motion.div>
              ))}

              {isLoading && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex gap-2 sm:gap-3">
                  <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg bg-gradient-to-br from-purple-500 to-cyan-500 flex items-center justify-center text-white text-xs sm:text-sm font-bold">N</div>
                  <div className={`px-3 sm:px-4 py-2.5 sm:py-3 rounded-2xl rounded-bl-md ${isDark ? 'bg-gray-800' : 'bg-gray-100'}`}>
                    <div className="flex gap-1">
                      <span className="typing-dot w-2 h-2 rounded-full bg-gray-500" />
                      <span className="typing-dot w-2 h-2 rounded-full bg-gray-500" />
                      <span className="typing-dot w-2 h-2 rounded-full bg-gray-500" />
                    </div>
                  </div>
                </motion.div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* Input Area */}
        <div className={`px-2 sm:px-4 py-2 sm:py-3 border-t ${isDark ? 'bg-[#0f0f1a] border-gray-800' : 'bg-gray-50 border-gray-200'}`}>
          <div className="max-w-3xl mx-auto">
            <div className="flex items-end gap-1.5 sm:gap-2">
              {/* Model Selector — compact on mobile */}
              <div className="relative hidden sm:block">
                <select
                  value={store.selectedModel}
                  onChange={(e) => store.setSelectedModel(e.target.value)}
                  className={`appearance-none pl-3 pr-7 py-2.5 rounded-xl text-sm border cursor-pointer ${
                    isDark ? 'bg-gray-800 border-gray-700 text-gray-300' : 'bg-gray-100 border-gray-300 text-gray-700'
                  }`}
                >
                  {aiModels.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
                </select>
                <ChevronDown className="w-3 h-3 absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 pointer-events-none" />
              </div>

              {/* Mobile: model selector as icon button */}
              <div className="relative sm:hidden">
                <select
                  value={store.selectedModel}
                  onChange={(e) => store.setSelectedModel(e.target.value)}
                  className={`appearance-none w-10 h-10 rounded-xl text-sm border cursor-pointer text-center pl-0 pr-0 ${
                    isDark ? 'bg-gray-800 border-gray-700 text-gray-300' : 'bg-gray-100 border-gray-300 text-gray-700'
                  }`}
                >
                  {aiModels.map(m => <option key={m.id} value={m.id}>{m.name.split(' ')[0]}</option>)}
                </select>
              </div>

              {/* Text Input */}
              <textarea
                ref={inputRef}
                value={inputValue}
                onChange={(e) => {
                  setInputValue(e.target.value)
                  e.target.style.height = 'auto'
                  e.target.style.height = Math.min(e.target.scrollHeight, 200) + 'px'
                }}
                onKeyDown={handleKeyDown}
                placeholder="Ask anything..."
                rows={1}
                className={`flex-1 px-3 sm:px-4 py-2.5 rounded-xl text-sm resize-none outline-none border transition-all ${
                  isDark ? 'bg-gray-800 border-gray-700 text-white placeholder-gray-500 focus:border-purple-500' : 'bg-gray-100 border-gray-300 text-gray-900 placeholder-gray-400 focus:border-purple-500'
                }`}
                style={{ minHeight: '42px', maxHeight: '200px' }}
              />

              {/* Send Button */}
              <button
                onClick={handleSend}
                disabled={isLoading || !inputValue.trim()}
                className={`p-2.5 rounded-xl transition-all shrink-0 active:scale-95 ${
                  isLoading || !inputValue.trim()
                    ? isDark ? 'bg-gray-800 text-gray-600' : 'bg-gray-200 text-gray-400'
                    : 'bg-gradient-to-r from-purple-600 to-cyan-600 text-white hover:opacity-90'
                }`}
              >
                {isLoading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <ArrowUp className="w-4 h-4" />}
              </button>
            </div>

            <div className="flex items-center justify-between mt-1">
              <p className="text-[10px] text-gray-500">Nexa can make mistakes</p>
              <div className="flex items-center gap-2">
                {/* TTS quick toggle in input area */}
                {tts.isEnabled && (
                  <button
                    onClick={() => tts.stop()}
                    className="text-[10px] text-purple-400 flex items-center gap-1"
                  >
                    <Volume2 className="w-3 h-3" />
                    {tts.isSpeaking ? 'Speaking...' : 'Voice On'}
                  </button>
                )}
                <kbd className="hidden sm:inline-flex px-1 py-0.5 rounded bg-gray-800 font-mono text-[9px] text-gray-500">Ctrl+K</kbd>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* ══════ COMMAND PALETTE ══════ */}
      <AnimatePresence>
        {store.showSearch && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/60 z-40" onClick={() => store.setShowSearch(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}
              className="fixed top-[10%] sm:top-[20%] left-1/2 -translate-x-1/2 w-[92%] sm:w-[90%] max-w-lg z-50"
            >
              <div className={`rounded-2xl border shadow-2xl overflow-hidden ${isDark ? 'bg-[#0f0f1a] border-gray-700' : 'bg-white border-gray-200'}`}>
                <div className={`flex items-center gap-3 px-4 py-3 border-b ${isDark ? 'border-gray-700' : 'border-gray-200'}`}>
                  <Search className="w-4 h-4 text-gray-500" />
                  <input
                    value={commandQuery}
                    onChange={(e) => setCommandQuery(e.target.value)}
                    placeholder="Type a command..."
                    className={`flex-1 bg-transparent outline-none text-sm placeholder-gray-500 ${isDark ? 'text-gray-200' : 'text-gray-900'}`}
                    autoFocus
                  />
                  <kbd className={`text-[10px] text-gray-500 px-1.5 py-0.5 rounded ${isDark ? 'bg-gray-800' : 'bg-gray-200'}`}>ESC</kbd>
                </div>
                <div className="max-h-64 overflow-y-auto py-1">
                  {filteredCommands.map(item => (
                    <button
                      key={item.id}
                      onClick={() => { item.action(); store.setShowSearch(false); setCommandQuery('') }}
                      className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-all ${
                        isDark ? 'hover:bg-gray-800 text-gray-300' : 'hover:bg-gray-100 text-gray-700'
                      }`}
                    >
                      <span className="text-gray-500">{item.icon}</span>
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* ══════ DASHBOARD DIALOG ══════ */}
      <AnimatePresence>
        {store.showDashboard && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/60 z-40" onClick={() => store.setShowDashboard(false)}
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="fixed inset-2 sm:inset-4 md:inset-[10%] z-50 overflow-hidden"
            >
              <div className={`h-full rounded-2xl border shadow-2xl overflow-y-auto ${isDark ? 'bg-[#0f0f1a] border-gray-700' : 'bg-white border-gray-200'}`}>
                <div className={`flex items-center justify-between px-4 sm:px-6 py-4 border-b sticky top-0 z-10 ${isDark ? 'border-gray-800 bg-[#0f0f1a]' : 'border-gray-200 bg-white'}`}>
                  <h2 className="text-lg font-bold">Dashboard</h2>
                  <button onClick={() => store.setShowDashboard(false)} className="p-1 rounded-lg hover:bg-gray-800"><X className="w-5 h-5" /></button>
                </div>
                <div className="p-4 sm:p-6 grid grid-cols-2 md:grid-cols-4 gap-3 sm:gap-4">
                  {[
                    { label: 'Total Messages', value: store.totalMessages, icon: <MessageSquare className="w-5 h-5" />, color: 'text-purple-400' },
                    { label: 'Tokens Used', value: store.tokenCount, icon: <Zap className="w-5 h-5" />, color: 'text-cyan-400' },
                    { label: 'Avg Response', value: `${avgResponseTime}ms`, icon: <Clock className="w-5 h-5" />, color: 'text-amber-400' },
                    { label: 'Uptime', value: formatUptime(store.uptimeStart), icon: <Activity className="w-5 h-5" />, color: 'text-emerald-400' },
                  ].map(stat => (
                    <div key={stat.label} className={`p-3 sm:p-4 rounded-xl border ${isDark ? 'bg-gray-800/50 border-gray-700' : 'bg-gray-50 border-gray-200'}`}>
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-[10px] sm:text-xs text-gray-500">{stat.label}</span>
                        <span className={stat.color}>{stat.icon}</span>
                      </div>
                      <p className="text-lg sm:text-2xl font-bold">{stat.value}</p>
                    </div>
                  ))}
                </div>

                {/* VRAM Gauge */}
                <div className="px-4 sm:px-6 pb-4 sm:pb-6">
                  <div className={`p-4 sm:p-6 rounded-xl border ${isDark ? 'bg-gray-800/50 border-gray-700' : 'bg-gray-50 border-gray-200'}`}>
                    <h3 className="text-sm font-semibold mb-4 flex items-center gap-2"><Cpu className="w-4 h-4" /> VRAM Usage</h3>
                    <div className="flex items-center justify-center">
                      <VramGauge usage={store.vramUsage} />
                    </div>
                    <p className="text-center text-xs text-gray-500 mt-2">RTX 3090 — 24GB GDDR6X</p>
                  </div>
                </div>

                {/* Engine Usage */}
                {Object.keys(store.engineUsage).length > 0 && (
                  <div className="px-4 sm:px-6 pb-4 sm:pb-6">
                    <div className={`p-4 sm:p-6 rounded-xl border ${isDark ? 'bg-gray-800/50 border-gray-700' : 'bg-gray-50 border-gray-200'}`}>
                      <h3 className="text-sm font-semibold mb-4 flex items-center gap-2"><Server className="w-4 h-4" /> Engine Usage</h3>
                      <div className="space-y-2">
                        {Object.entries(store.engineUsage).map(([engine, count]) => {
                          const maxCount = Math.max(...Object.values(store.engineUsage))
                          return (
                            <div key={engine} className="flex items-center gap-3">
                              <span className="text-xs text-gray-400 w-20 sm:w-24 truncate">{engine}</span>
                              <div className="flex-1 h-2 bg-gray-800 rounded-full overflow-hidden">
                                <div className="h-full rounded-full bg-gradient-to-r from-purple-500 to-cyan-500" style={{ width: `${(count / maxCount) * 100}%` }} />
                              </div>
                              <span className="text-xs text-gray-400">{count}</span>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  </div>
                )}

                {/* Datacenter Status */}
                <div className="px-4 sm:px-6 pb-4 sm:pb-6">
                  <div className={`p-4 sm:p-6 rounded-xl border ${isDark ? 'bg-gray-800/50 border-gray-700' : 'bg-gray-50 border-gray-200'}`}>
                    <h3 className="text-sm font-semibold mb-4 flex items-center gap-2"><Database className="w-4 h-4" /> Datacenter</h3>
                    <div className="flex items-center gap-3">
                      <span className={`w-3 h-3 rounded-full ${
                        store.datacenterStatus === 'online' ? 'bg-emerald-500' :
                        store.datacenterStatus === 'offline' ? 'bg-red-500' : 'bg-amber-500 animate-pulse'
                      }`} />
                      <span className="text-sm">{store.datacenterStatus === 'online' ? 'Online — RTX 3090 Connected' : store.datacenterStatus === 'offline' ? 'Offline — Using Cloud' : 'Checking...'}</span>
                      {store.pingLatency && <span className="text-xs text-gray-500">{store.pingLatency}ms latency</span>}
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* ══════ NOTIFICATIONS PANEL ══════ */}
      <AnimatePresence>
        {store.showNotifications && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/40 z-40" onClick={() => store.setShowNotifications(false)}
            />
            <motion.div
              initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }}
              className={`fixed right-2 sm:right-4 top-14 w-[calc(100%-16px)] sm:w-80 max-h-[70vh] rounded-xl border shadow-xl z-50 overflow-hidden ${isDark ? 'bg-[#0f0f1a] border-gray-700' : 'bg-white border-gray-200'}`}
            >
              <div className={`flex items-center justify-between px-4 py-3 border-b ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
                <span className="text-sm font-semibold">Notifications</span>
                <button onClick={() => store.markAllNotificationsRead()} className="text-[10px] text-purple-400 hover:text-purple-300">Mark all read</button>
              </div>
              <div className="overflow-y-auto max-h-60">
                {store.notifications.length === 0 ? (
                  <p className="text-center text-gray-500 text-sm py-6">No notifications</p>
                ) : (
                  store.notifications.map(notif => (
                    <div key={notif.id}
                      onClick={() => store.markNotificationRead(notif.id)}
                      className={`px-4 py-3 border-b cursor-pointer transition-all ${
                        notif.read ? 'opacity-50' : ''
                      } ${isDark ? 'border-gray-800 hover:bg-gray-800/50' : 'border-gray-100 hover:bg-gray-50'}`}
                    >
                      <div className="flex items-center gap-2">
                        {notif.type === 'success' && <CheckCircle className="w-3.5 h-3.5 text-emerald-400" />}
                        {notif.type === 'error' && <AlertCircle className="w-3.5 h-3.5 text-red-400" />}
                        {notif.type === 'warning' && <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />}
                        {notif.type === 'info' && <Info className="w-3.5 h-3.5 text-sky-400" />}
                        <span className="text-xs font-medium">{notif.title}</span>
                      </div>
                      <p className="text-[11px] text-gray-500 mt-0.5">{notif.message}</p>
                    </div>
                  ))
                )}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* ══════ SETTINGS DIALOG ══════ */}
      <AnimatePresence>
        {store.showSettings && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/60 z-40" onClick={() => store.setShowSettings(false)}
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
              className="fixed top-[5%] sm:top-[10%] left-1/2 -translate-x-1/2 w-[92%] sm:w-[90%] max-w-md z-50"
            >
              <div className={`rounded-2xl border shadow-2xl overflow-hidden ${isDark ? 'bg-[#0f0f1a] border-gray-700' : 'bg-white border-gray-200'}`}>
                <div className={`flex items-center justify-between px-4 sm:px-6 py-4 border-b ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
                  <h2 className="text-lg font-bold">Settings</h2>
                  <button onClick={() => store.setShowSettings(false)} className="p-1 rounded-lg hover:bg-gray-800"><X className="w-5 h-5" /></button>
                </div>
                <div className="p-4 sm:p-6 space-y-4">
                  {/* Theme */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm">Theme</span>
                    <button onClick={() => store.toggleTheme()} className={`px-3 py-1.5 rounded-lg text-sm ${isDark ? 'bg-gray-800 text-gray-300' : 'bg-gray-200 text-gray-700'}`}>
                      {isDark ? <><Sun className="w-4 h-4 inline mr-1" /> Light</> : <><Moon className="w-4 h-4 inline mr-1" /> Dark</>}
                    </button>
                  </div>
                  {/* Hands-Free Voice */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm flex items-center gap-2">
                      <Volume2 className="w-4 h-4 text-purple-400" />
                      Hands-Free Voice
                    </span>
                    <button onClick={() => tts.toggleTts()} className={`px-3 py-1.5 rounded-lg text-sm ${tts.isEnabled ? 'bg-purple-600 text-white' : isDark ? 'bg-gray-800 text-gray-400' : 'bg-gray-200 text-gray-600'}`}>
                      {tts.isEnabled ? 'ON' : 'OFF'}
                    </button>
                  </div>
                  {/* Auto-Speak */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm">Auto-speak responses</span>
                    <button onClick={() => store.toggleTtsAutoSpeak()} className={`px-3 py-1.5 rounded-lg text-sm ${store.ttsAutoSpeak ? 'bg-purple-600 text-white' : isDark ? 'bg-gray-800 text-gray-400' : 'bg-gray-200 text-gray-600'}`}>
                      {store.ttsAutoSpeak ? 'ON' : 'OFF'}
                    </button>
                  </div>
                  {/* Reasoning Trace */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm">Reasoning Trace</span>
                    <button onClick={() => store.toggleReasoningTrace()} className={`px-3 py-1.5 rounded-lg text-sm ${store.showReasoningTrace ? 'bg-purple-600 text-white' : isDark ? 'bg-gray-800 text-gray-400' : 'bg-gray-200 text-gray-600'}`}>
                      {store.showReasoningTrace ? 'On' : 'Off'}
                    </button>
                  </div>
                  {/* Skip Judge */}
                  <div className="flex items-center justify-between">
                    <span className="text-sm">Skip Judge</span>
                    <button onClick={() => store.toggleSkipJudge()} className={`px-3 py-1.5 rounded-lg text-sm ${store.skipJudge ? 'bg-amber-600 text-white' : isDark ? 'bg-gray-800 text-gray-400' : 'bg-gray-200 text-gray-600'}`}>
                      {store.skipJudge ? 'Bypassed' : 'Enabled'}
                    </button>
                  </div>
                  {/* Clear History */}
                  <div className={`pt-2 border-t ${isDark ? 'border-gray-800' : 'border-gray-200'}`}>
                    <button onClick={() => { store.setState({ conversations: [] }); store.setActiveConversation(null) }}
                      className="w-full px-3 py-2 rounded-lg text-sm bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 transition-all">
                      <Trash2 className="w-4 h-4 inline mr-2" />Clear All Conversations
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  )
}
