package main

import (
	"fmt"

	"github.com/cgruber/brackets/golang/brackets"
)

var text string

func init() {
	text = "Hello, (world)!"
}

func main() {
	fmt.Printf("Checking text for brackets:\n%s\n", text)
	err := brackets.BalancedBrackets(text)
	if err != nil {
		fmt.Printf("    Mismatched brackets in text: %v\v", err)
	} else {
		fmt.Printf("    Ok - brackets match")
	}
}
