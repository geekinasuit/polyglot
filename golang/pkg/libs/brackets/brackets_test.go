package brackets_test

import (
	"testing"

	"github.com/geekinasuit/polyglot/golang/pkg/libs/brackets"
	"github.com/stretchr/testify/require"
)

func Test_Empty_Success(t *testing.T) {
	err := brackets.BalancedBrackets("")
	require.NoError(t, err)
}

func Test_SimpleBrackets_Success(t *testing.T) {
	err := brackets.BalancedBrackets("()")
	require.NoError(t, err)
}

func Test_SimpleBrackets_Fail_RemainingOpen(t *testing.T) {
	err := brackets.BalancedBrackets("(()")
	require.ErrorContains(t, err, "opening brackets without closing brackets found: [(]")
}

func Test_SimpleBrackets_Fail_ExtraClosed(t *testing.T) {
	err := brackets.BalancedBrackets("(()))")
	require.ErrorContains(t, err, "closing bracket ) with no opening bracket at rune 5")
}

func Test_SimpleBrackets_Fail_Mismatched(t *testing.T) {
	err := brackets.BalancedBrackets("((])")
	require.ErrorContains(t, err, "closing bracket ] at rune 3 mismatched with last opening bracket (")
}

func Test_ComplexText_Success(t *testing.T) {
	err := brackets.BalancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).")
	require.NoError(t, err)
}

func Test_ComplexMixedText_Success(t *testing.T) {
	err := brackets.BalancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget [to {nest}].).")
	require.NoError(t, err)
}

func Test_ComplexText_Mismatched(t *testing.T) {
	err := brackets.BalancedBrackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).")
	require.ErrorContains(t, err, "closing bracket ] at rune 36 mismatched with last opening bracket (")
}

func Test_Foo(t *testing.T) {
	something := brackets.Something{Id: 2, Name: "blah", Labels: []string{"Q", "R"}}
	require.Equal(t, int64(2), something.Id)
	require.Equal(t, "blah", something.Name)
	require.Equal(t, []string{"Q", "R"}, something.Labels)
}
