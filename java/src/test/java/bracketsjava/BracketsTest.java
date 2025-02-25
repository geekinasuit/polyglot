package bracketsjava;

import static bracketsjava.Brackets.balancedBrackets;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.geekinasuit.polyglot.example.protos.Something;
import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class BracketsTest {

  @Test
  public void testEmptySuccess() throws Exception {
    balancedBrackets("");
  }

  @Test
  public void testSimpleBracketsSuccess() throws Exception {
    balancedBrackets("()");
  }

  @Test
  public void testSimpleBracketsFailRemainingOpen() {
    bracketsjava.BracketsNotBalancedException err = assertThrows(
            bracketsjava.BracketsNotBalancedException.class,
            () -> balancedBrackets("(()")
    );
    assertThat(err).hasMessageThat().contains("opening brackets without closing brackets found: [(]");
  }

  @Test
  public void testSimpleBracketsFailExtraClosed() {
    bracketsjava.BracketsNotBalancedException err = assertThrows(
            bracketsjava.BracketsNotBalancedException.class,
            () -> balancedBrackets("(()))")
    );
    assertThat(err).hasMessageThat().contains("closing bracket ) with no opening bracket at char 5");

  }

  @Test
  public void testSimpleBracketsFailMismatched()  throws Exception{
    bracketsjava.BracketsNotBalancedException err = assertThrows(
            bracketsjava.BracketsNotBalancedException.class,
            () -> balancedBrackets("((])")
    );
    assertThat(err).hasMessageThat().contains("closing bracket ] at char 3 mismatched with last opening bracket (");
  }

  @Test
  public void testComplexTextSuccess() throws Exception {
    balancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).");
  }

  @Test
  public void testComplexMixedTextSuccess() throws Exception {
    balancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget [to {nest}].).");
  }

  @Test
  public void testComplexTextMismatched()  throws Exception{
    bracketsjava.BracketsNotBalancedException err = assertThrows(
      bracketsjava.BracketsNotBalancedException.class, () ->
      balancedBrackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).")
    );
    assertThat(err).hasMessageThat().contains("closing bracket ] at char 36 mismatched with last opening bracket (");
  }

  @Test
  public void testFoo()  throws Exception{
    Something actual = Brackets.foo();
    assertThat(actual.getId()).isEqualTo(1);
    assertThat(actual.getName()).isEqualTo("Foo");
    assertThat(actual.getLabelsList()).containsExactly("A", "B").inOrder();
  }
}
