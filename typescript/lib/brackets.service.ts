import { Injectable } from '@nestjs/common';

@Injectable()
export class BracketsService {
    private readonly closedParentheses: Record<string, string> = {
        ')': '(',
        '}': '{',
        ']': '[',
    };
    private readonly openParentheses = new Set(Object.values(this.closedParentheses));

    validate(text: string): { isBalanced: boolean; error?: string } {
        if (!text) return { isBalanced: true };

        const stack: string[] = [];

        for (let i = 0; i < text.length; i++) {
            const char = text[i];

            if (this.openParentheses.has(char)) {
                stack.push(char);
            } else if (char in this.closedParentheses) {
                const expectedOpen = this.closedParentheses[char];
                const lastOpen = stack.pop();

                if (!lastOpen) {
                    return {
                        isBalanced: false,
                        error: `closing bracket ${char} with no opening bracket at char ${i + 1}`
                    };
                }
                if (lastOpen !== expectedOpen) {
                    return {
                        isBalanced: false,
                        error: `closing bracket ${char} at char ${i + 1} mismatched with last opening bracket ${lastOpen}`
                    };
                }
            }
        }

        if (stack.length > 0) {
            return {
                isBalanced: false,
                error: `opening brackets without closing brackets found: [${stack.join(', ')}]`
            };
        }

        return { isBalanced: true };
    }
}