import { Controller, Post, Body, HttpCode } from '@nestjs/common';
import { GrpcMethod } from '@nestjs/microservices';
import { BracketsService } from '../lib/brackets.service.js'; // Relative import works because of tsconfig root
import { BalanceRequest, BalanceResponse } from '../generated/brackets_service';

@Controller('brackets')
export class BracketsController {
    constructor(private readonly bracketsService: BracketsService) {}

    @GrpcMethod('BalanceBrackets', 'Balance')
    balanceGrpc(data: BalanceRequest): BalanceResponse {
        return this.process(data.statement);
    }

    @Post('balance')
    @HttpCode(200)
    balanceHttp(@Body() body: BalanceRequest): BalanceResponse {
        return this.process(body.statement);
    }

    private process(statement: string): BalanceResponse {
        try {
            const result = this.bracketsService.validate(statement);
            return {
                succeeded: true,
                isBalanced: result.isBalanced,
                error: result.error || '',
            };
        } catch (e) {
            return {
                succeeded: false,
                isBalanced: false,
                error: e instanceof Error ? e.message : 'Unknown error',
            };
        }
    }
}