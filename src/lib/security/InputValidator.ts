export interface ValidationResult {
  safe: boolean;
  reason?: string;
}

export class InputValidator {
  private blacklist = [
    'DROP TABLE',
    'DELETE FROM',
    'INSERT INTO',
    'UPDATE ',
    'TRUNCATE',
    'ignore previous instructions',
    'bypass safety',
    'system override',
    'you are now a',
    'ignore all rules',
    '<script',
    'javascript:',
    'onload=',
    'onerror='
  ];

  public validate(input: string): ValidationResult {
    if (!input) return { safe: true };

    // 1. Check for HTML/script injection (lightweight, no DOM dependency)
    const hasHtmlTags = /<[a-z][\s\S]*>/i.test(input);
    const hasJsProtocol = /javascript\s*:/i.test(input);
    const hasEventHandler = /\bon\w+\s*=/i.test(input);
    
    if (hasHtmlTags || hasJsProtocol || hasEventHandler) {
      return { safe: false, reason: 'Inyección de HTML/Script detectada' };
    }

    // 2. Check against jailbreak patterns
    const lowerInput = input.toLowerCase();
    for (const pattern of this.blacklist) {
      if (lowerInput.includes(pattern.toLowerCase())) {
        return { safe: false, reason: `Patrón no seguro detectado: ${pattern}` };
      }
    }

    // 3. Token limit (approximate by words)
    const words = input.trim().split(/\s+/).length;
    if (words > 4000) {
      return { safe: false, reason: 'El mensaje excede el límite de palabras permitido' };
    }

    return { safe: true };
  }
}
