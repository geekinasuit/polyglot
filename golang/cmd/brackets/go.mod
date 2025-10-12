module github.com/geekinasuit/polyglot/golang/cmd/brackets

go 1.25.2

// Temporary to force it to ignore the missing stuff on github. go modules suck.
replace github.com/geekinasuit/polyglot/golang/pkg/libs/brackets => ../../pkg/libs/brackets

require (
	github.com/geekinasuit/polyglot/golang/pkg/libs/brackets v0.0.0-00010101000000-000000000000
	github.com/spf13/cobra v1.10.1
)

require (
	github.com/emirpasic/gods v1.18.1 // indirect
	github.com/inconshreveable/mousetrap v1.1.0 // indirect
	github.com/spf13/pflag v1.0.9 // indirect
	google.golang.org/protobuf v1.36.10 // indirect
)
