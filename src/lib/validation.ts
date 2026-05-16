import { z } from 'zod';

export const chatSchema = z.object({
  messages: z.array(z.object({
    role: z.enum(['user', 'assistant', 'system']),
    content: z.string(),
  })).min(1).max(50),
  mode: z.string().optional(),
});

export const visionSchema = z.object({
  image: z.string().min(1),
  mimeType: z.string().optional(),
  question: z.string().max(5000).optional(),
  model: z.string().optional(),
});

export const codeGenSchema = z.object({
  prompt: z.string().min(1).max(10000),
  language: z.string().optional(),
  framework: z.string().optional(),
});

export const aiSchema = z.object({
  provider: z.enum(['gemini', 'anthropic', 'auto']).optional(),
  model: z.string().optional(),
  messages: z.array(z.object({
    role: z.enum(['user', 'assistant', 'system']),
    content: z.string(),
  })).min(1).max(50),
  temperature: z.number().min(0).max(2).optional(),
  max_tokens: z.number().min(1).max(32000).optional(),
});
