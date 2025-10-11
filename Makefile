# Makefile for the Go build

go-generate:
	go generate ./golang/pkg/libs/brackets/generate.go

go-build: go-generate
	mkdir -p out
	go build -o out/brackets_go ./golang/cmd/brackets

go-test: go-build
	go test ./golang/pkg/libs/brackets/...

go-run: go-build
	out/brackets_go