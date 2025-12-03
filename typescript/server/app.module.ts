import { Module } from '@nestjs/common';
import { BracketsController } from './brackets.controller';
import { BracketsService } from '../lib/brackets.service.js';

@Module({
    imports: [],
    controllers: [BracketsController],
    providers: [BracketsService],
})
export class AppModule {}