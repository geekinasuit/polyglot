import unittest

import grpc

import protobuf.brackets_service_pb2 as brackets_pb
import protobuf.brackets_service_pb2_grpc as brackets_grpc

class GrpcTest(unittest.TestCase):
    def testGrpcStubsImport(self):
        # Test that gRPC stubs can be imported
        self.assertIsNotNone(brackets_grpc.BalanceBracketsStub)
        self.assertIsNotNone(brackets_grpc.BalanceBracketsServicer)

    def testChannelCreation(self):
        # Test basic channel creation (will fail to connect, but setup is tested)
        with self.assertRaises(grpc.RpcError):
            channel = grpc.insecure_channel('localhost:0')
            stub = brackets_grpc.BalanceBracketsStub(channel)
            # Try to call a method, should fail since no server
            stub.BalanceBrackets(protobuf.brackets_service_pb2.BalanceBracketsRequest(text="()"))

if __name__ == "__main__":
    unittest.main()