package main

import (
	"io"
	"os"
	"strings"
)

type Stringer interface {
	String() string
}

func main() {
	sprintln("Hello World")
}
func wprintln(writer io.Writer, elements ...any) {
	var sb = strings.Builder{}
	for _, e := range elements {
		switch e.(type) {
		case string:
			sb.WriteString(e.(string))
		case Stringer:
			sb.WriteString((e.(Stringer)).String())
		default:
			panic("Invalid input type.")
		}
	}
	_, _ = io.WriteString(writer, sb.String())
}
func sprintln(elements ...any) {
	wprintln(os.Stdout, elements)
}
