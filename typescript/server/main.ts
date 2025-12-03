import { NestFactory } from '@nestjs/core';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import { AppModule } from './app.module';
import { join } from 'path';

async function bootstrap() {
    const app = await NestFactory.create(AppModule);

    app.connectMicroservice<MicroserviceOptions>({
        transport: Transport.GRPC,
        options: {
            url: '0.0.0.0:8888',
            package: 'brackets_service',
            // Note: In Bazel, this path resolution can be tricky.
            // For now, this works for the Native build.
            // We will refine the Bazel runfiles path in the next step.
            protoPath: join(__dirname, '../../../../protobuf/brackets_service.proto'),
        },
    });

    await app.startAllMicroservices();
    await app.listen(3000);
}
bootstrap();