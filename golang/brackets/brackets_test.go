package brackets

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func Test_Empty_Success(t *testing.T) {
	err := BalancedBrackets("")
	require.NoError(t, err)
}

func Test_SimpleBrackets_Success(t *testing.T) {
	err := BalancedBrackets("()")
	require.NoError(t, err)
}

func Test_SimpleBrackets_Fail_RemainingOpen(t *testing.T) {
	err := BalancedBrackets("(()")
	require.ErrorContains(t, err, "opening brackets without closing brackets found: [(]")
}

func Test_SimpleBrackets_Fail_ExtraClosed(t *testing.T) {
	err := BalancedBrackets("(()))")
	require.ErrorContains(t, err, "closing bracket ) with no opening bracket at rune 5")
}

func Test_SimpleBrackets_Fail_Mismatched(t *testing.T) {
	err := BalancedBrackets("((])")
	require.ErrorContains(t, err, "closing bracket ] at rune 3 mismatched with last opening bracket (")
}

func Test_ComplexText_Success(t *testing.T) {
	err := BalancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).")
	require.NoError(t, err)
}

func Test_ComplexText_Mismatched(t *testing.T) {
	err := BalancedBrackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).")
	require.ErrorContains(t, err, "closing bracket ] at rune 36 mismatched with last opening bracket (")
}
