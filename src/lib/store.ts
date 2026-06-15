import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface RoutingInfo {
  intent: string
  confidence: number
  engine: string
  reasoning: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  model?: string
  routing?: RoutingInfo
  judge?: {
    winner: string
    scores: Record<string, number>
    reasoning: string
  }
  datacenter?: boolean
  tokens?: number
  responseTime?: number
}

export interface Conversation {
  id: string
  title: string
  messages: Message[]
  createdAt: number
  updatedAt: number
  channelId: string
  model: string
}

export interface Channel {
  id: string
  name: string
  icon: string
  unread: number
  lastMessage: string
  lastTime: string
  color: string
}

export interface Notification {
  id: string
  title: string
  message: string
  time: number
  read: boolean
  type: 'info' | 'success' | 'warning' | 'error'
}

export type ThemeMode = 'dark' | 'light'

interface NexaState {
  // Theme
  theme: ThemeMode
  toggleTheme: () => void

  // Channels
  channels: Channel[]
  activeChannel: string
  setActiveChannel: (id: string) => void
  markChannelRead: (id: string) => void

  // Conversations
  conversations: Conversation[]
  activeConversation: string | null
  setActiveConversation: (id: string | null) => void
  createConversation: (channelId: string, model: string) => string
  addMessage: (conversationId: string, message: Message) => void
  deleteConversation: (id: string) => void
  getActiveConversation: () => Conversation | undefined

  // Datacenter
  datacenterStatus: 'online' | 'offline' | 'checking'
  setDatacenterStatus: (status: 'online' | 'offline' | 'checking') => void
  showReasoningTrace: boolean
  toggleReasoningTrace: () => void
  lastRouting: RoutingInfo | null
  setLastRouting: (routing: RoutingInfo | null) => void
  pingLatency: number | null
  setPingLatency: (ms: number | null) => void
  lastPingTime: number | null
  setLastPingTime: (time: number | null) => void

  // Stats
  totalMessages: number
  incrementMessages: () => void
  engineUsage: Record<string, number>
  trackEngine: (engine: string) => void
  tokenCount: number
  incrementTokens: (count: number) => void
  vramUsage: number
  setVramUsage: (usage: number) => void
  responseTimes: number[]
  addResponseTime: (ms: number) => void
  uptimeStart: number

  // Model override
  skipJudge: boolean
  toggleSkipJudge: () => void

  // Notifications
  notifications: Notification[]
  addNotification: (title: string, message: string, type?: Notification['type']) => void
  markNotificationRead: (id: string) => void
  markAllNotificationsRead: () => void
  clearNotifications: () => void
  unreadNotificationCount: () => number

  // Settings
  selectedModel: string
  setSelectedModel: (model: string) => void
  sidebarCollapsed: boolean
  setSidebarCollapsed: (collapsed: boolean) => void
  mobileSidebarOpen: boolean
  setMobileSidebarOpen: (open: boolean) => void
  showSettings: boolean
  setShowSettings: (show: boolean) => void
  showSearch: boolean
  setShowSearch: (show: boolean) => void
  showDashboard: boolean
  setShowDashboard: (show: boolean) => void
  showNotifications: boolean
  setShowNotifications: (show: boolean) => void
  userName: string
  setUserName: (name: string) => void

  // TTS / Hands-Free
  ttsEnabled: boolean
  toggleTts: () => void
  ttsSpeaking: boolean
  setTtsSpeaking: (speaking: boolean) => void
  ttsAutoSpeak: boolean
  toggleTtsAutoSpeak: () => void
  ttsVoiceIndex: number
  setTtsVoiceIndex: (index: number) => void
}

const defaultChannels: Channel[] = [
  { id: '1', name: 'General', icon: 'Hash', unread: 3, lastMessage: 'Nuevo modelo disponible', lastTime: '2m', color: '#a855f7' },
  { id: '2', name: 'Desarrollo', icon: 'Code', unread: 0, lastMessage: 'Build completado', lastTime: '15m', color: '#06b6d4' },
  { id: '3', name: 'Diseño', icon: 'Palette', unread: 1, lastMessage: 'Mockups actualizados', lastTime: '1h', color: '#f59e0b' },
  { id: '4', name: 'Marketing', icon: 'Globe', unread: 0, lastMessage: 'Campaña Q3 lista', lastTime: '3h', color: '#10b981' },
  { id: '5', name: 'Soporte', icon: 'Headphones', unread: 5, lastMessage: '3 tickets abiertos', lastTime: '5m', color: '#ef4444' },
  { id: '6', name: 'Datos', icon: 'BarChart3', unread: 2, lastMessage: 'Reporte mensual listo', lastTime: '30m', color: '#8b5cf6' },
  { id: '7', name: 'IA & ML', icon: 'Brain', unread: 0, lastMessage: 'Modelo entrenado 98% acc', lastTime: '2h', color: '#ec4899' },
]

export const useNexaStore = create<NexaState>()(
  persist(
    (set, get) => ({
      // Theme
      theme: 'dark',
      toggleTheme: () => set((s) => ({ theme: s.theme === 'dark' ? 'light' : 'dark' })),

      // Channels
      channels: defaultChannels,
      activeChannel: '1',
      setActiveChannel: (id) => set({ activeChannel: id }),
      markChannelRead: (id) => set((s) => ({
        channels: s.channels.map(c => c.id === id ? { ...c, unread: 0 } : c)
      })),

      // Conversations
      conversations: [],
      activeConversation: null,
      setActiveConversation: (id) => set({ activeConversation: id }),
      createConversation: (channelId, model) => {
        const id = Date.now().toString()
        const conv: Conversation = {
          id, title: 'Nueva conversacion', messages: [],
          createdAt: Date.now(), updatedAt: Date.now(), channelId, model
        }
        set((s) => ({ conversations: [conv, ...s.conversations], activeConversation: id }))
        return id
      },
      addMessage: (conversationId, message) => set((s) => ({
        conversations: s.conversations.map(c => {
          if (c.id !== conversationId) return c
          const msgs = [...c.messages, message]
          const title = c.messages.length === 0
            ? message.content.slice(0, 40) + (message.content.length > 40 ? '...' : '')
            : c.title
          return { ...c, messages: msgs, title, updatedAt: Date.now() }
        })
      })),
      deleteConversation: (id) => set((s) => ({
        conversations: s.conversations.filter(c => c.id !== id),
        activeConversation: s.activeConversation === id ? null : s.activeConversation
      })),
      getActiveConversation: () => {
        const s = get()
        return s.conversations.find(c => c.id === s.activeConversation)
      },

      // Datacenter
      datacenterStatus: 'checking',
      setDatacenterStatus: (status) => set({ datacenterStatus: status }),
      showReasoningTrace: true,
      toggleReasoningTrace: () => set((s) => ({ showReasoningTrace: !s.showReasoningTrace })),
      lastRouting: null,
      setLastRouting: (routing) => set({ lastRouting: routing }),
      pingLatency: null,
      setPingLatency: (ms) => set({ pingLatency: ms }),
      lastPingTime: null,
      setLastPingTime: (time) => set({ lastPingTime: time }),

      // Stats
      totalMessages: 0,
      incrementMessages: () => set((s) => ({ totalMessages: s.totalMessages + 1 })),
      engineUsage: {},
      trackEngine: (engine) => set((s) => ({ engineUsage: { ...s.engineUsage, [engine]: (s.engineUsage[engine] || 0) + 1 } })),
      tokenCount: 0,
      incrementTokens: (count) => set((s) => ({ tokenCount: s.tokenCount + count })),
      vramUsage: 0,
      setVramUsage: (usage) => set({ vramUsage: usage }),
      responseTimes: [],
      addResponseTime: (ms) => set((s) => ({ responseTimes: [...s.responseTimes.slice(-49), ms] })),
      uptimeStart: Date.now(),

      // Model override
      skipJudge: false,
      toggleSkipJudge: () => set((s) => ({ skipJudge: !s.skipJudge })),

      // Notifications
      notifications: [],
      addNotification: (title, message, type = 'info') => set((s) => ({
        notifications: [{ id: Date.now().toString(), title, message, time: Date.now(), read: false, type }, ...s.notifications].slice(0, 50)
      })),
      markNotificationRead: (id) => set((s) => ({
        notifications: s.notifications.map(n => n.id === id ? { ...n, read: true } : n)
      })),
      markAllNotificationsRead: () => set((s) => ({
        notifications: s.notifications.map(n => ({ ...n, read: true }))
      })),
      clearNotifications: () => set({ notifications: [] }),
      unreadNotificationCount: () => get().notifications.filter(n => !n.read).length,

      // Settings
      selectedModel: 'nexa-pro',
      setSelectedModel: (model) => set({ selectedModel: model }),
      sidebarCollapsed: false,
      setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),
      mobileSidebarOpen: false,
      setMobileSidebarOpen: (open) => set({ mobileSidebarOpen: open }),
      showSettings: false,
      setShowSettings: (show) => set({ showSettings: show }),
      showSearch: false,
      setShowSearch: (show) => set({ showSearch: show }),
      showDashboard: false,
      setShowDashboard: (show) => set({ showDashboard: show }),
      showNotifications: false,
      setShowNotifications: (show) => set({ showNotifications: show }),
      userName: 'Usuario Nexa',
      setUserName: (name) => set({ userName: name }),

      // TTS / Hands-Free
      ttsEnabled: false,
      toggleTts: () => set((s) => ({ ttsEnabled: !s.ttsEnabled })),
      ttsSpeaking: false,
      setTtsSpeaking: (speaking) => set({ ttsSpeaking: speaking }),
      ttsAutoSpeak: true,
      toggleTtsAutoSpeak: () => set((s) => ({ ttsAutoSpeak: !s.ttsAutoSpeak })),
      ttsVoiceIndex: 0,
      setTtsVoiceIndex: (index) => set({ ttsVoiceIndex: index }),
    }),
    {
      name: 'nexa-store',
      partialize: (state) => ({
        theme: state.theme,
        conversations: state.conversations,
        selectedModel: state.selectedModel,
        userName: state.userName,
        activeChannel: state.activeChannel,
        totalMessages: state.totalMessages,
        engineUsage: state.engineUsage,
        showReasoningTrace: state.showReasoningTrace,
        tokenCount: state.tokenCount,
        skipJudge: state.skipJudge,
        vramUsage: state.vramUsage,
        responseTimes: state.responseTimes,
        uptimeStart: state.uptimeStart,
        notifications: state.notifications,
        ttsEnabled: state.ttsEnabled,
        ttsAutoSpeak: state.ttsAutoSpeak,
        ttsVoiceIndex: state.ttsVoiceIndex,
      })
    }
  )
)
