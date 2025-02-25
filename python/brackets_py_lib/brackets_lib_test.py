from python.brackets_py_lib.brackets_lib import balanced_brackets, BracketsNotBalancedException, foo

import unittest

from truth.truth import AssertThat

class ExampleTest(unittest.TestCase):
    def testEmptySuccess(self):
        balanced_brackets("")

    def testSimpleBracketsSuccess(self):
        balanced_brackets("()")

    def testSimpleBracketsFailRemainingOpen(self):
        with self.assertRaises(BracketsNotBalancedException) as err:
            balanced_brackets("(()")
        AssertThat(err.exception.message).IsEqualTo("opening brackets without closing brackets found: [(]")

    def testSimpleBracketsFailExtraClosed(self):
        with self.assertRaises(BracketsNotBalancedException) as err:
            balanced_brackets("(()))")
        AssertThat(err.exception.message).IsEqualTo("closing bracket ) with no opening bracket at char 5")

    def testSimpleBracketsFailMismatched(self):
        with self.assertRaises(BracketsNotBalancedException) as err:
            balanced_brackets("((])")
        AssertThat(err.exception.message).IsEqualTo("closing bracket ] at char 3 mismatched with last opening bracket (")

    def testComplexTextSuccess(self):
        balanced_brackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).")

    def testComplexMixedTextSuccess(self):
        balanced_brackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget [to {nest}].).")

    def testComplexTextMismatched(self):
        with self.assertRaises(BracketsNotBalancedException) as err:
            balanced_brackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).")
        AssertThat(err.exception.message).IsEqualTo("closing bracket ] at char 36 mismatched with last opening bracket (")

    def testFoo(self):
        something = foo()
        pass # Should return a Something, but we can't import it.


if __name__ == "__main__":
    unittest.main()