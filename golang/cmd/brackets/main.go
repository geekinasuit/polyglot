package main

import (
	"fmt"
	"os"

	"github.com/geekinasuit/polyglot/golang/pkg/libs/brackets"
	"github.com/spf13/cobra"
)

var text string

func init() {
	text = "Hello, (world)!"
}

func main() {
	if err := rootCmd.Execute(); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

var rootCmd = &cobra.Command{
	Use:   "brackets",
	Short: "Brackets checks for the use of balanced brackets in a piece of text.",
	Run: func(cmd *cobra.Command, args []string) {
		// TODO: Check input from stdin and file.
		fmt.Printf("Checking text for brackets:\n%s\n", text)
		err := brackets.BalancedBrackets(text)
		if err != nil {
			fmt.Printf("    Mismatched brackets in text: %v\n", err)
			os.Exit(1)
		} else {
			fmt.Printf("    Ok - brackets match\n")
		}
	},
}
