use std::collections::{HashMap, HashSet};
use example_proto::example_protos::Something;

pub fn balanced_brackets(text: &str) -> Result<(), String> {
    let closing_brackets = HashMap::from([
        (')', '('),
        (']', '['),
        ('}', '{'),
    ]);
    let opening_brackets: HashSet<char> = closing_brackets.values().cloned().collect();
    let char_vec: Vec<char> = text.chars().collect();
    let mut stack: Vec<char> = vec![];
    for (i, c) in char_vec.iter().enumerate() {
        if opening_brackets.contains(c) {
            stack.push(*c)
        } else if closing_brackets.contains_key(c) {
            match stack.pop() {
                None => {
                    return Err(format!(
                        "closing bracket {close} with no opening bracket at char {pos}",
                        close = c,
                        pos = i+1,
                    ));
                }
                Some(open) => {
                    if *closing_brackets.get(c).unwrap() != open {
                        return Err(format!(
                            "closing bracket {close} at char {pos} mismatched with last opening bracket {open}",
                            close = c,
                            open = open,
                            pos = i+1,
                        ))
                    }
                }
            }
        }
    }
    if !stack.is_empty() {
        let brackets = stack.iter().map(|x| x.to_string()).collect::<Vec<String>>();
        return Err(format!(
            "opening brackets without closing brackets found: [{stack}]",
            stack = brackets.join(","),
        ));
    }

    Ok(())
}

fn foo() -> Option<Something> {
    return Some(Something { id: 1, name: "foo".to_string(), labels: vec!{ "a".to_string(), "b".to_string() } })
}

#[cfg(test)]
mod tests {
    use assertor::*;

    use super::*;

    #[test]
    fn test_empty_success() {
        let r = balanced_brackets("");
        assert_that!(r).is_ok();
    }

    #[test]
    fn test_simple_brackets_success() {
        let r = balanced_brackets("()");
        assert_that!(r).is_ok();
    }

    #[test]
    fn test_simple_brackets_fail_remaining_open() {
        let r = balanced_brackets("(()");
        let expected = "opening brackets without closing brackets found: [(]";
        assert_that!(r).is_err();
        assert_that!(r.err().unwrap()).is_equal_to(expected.to_string())
    }

    #[test]
    fn test_simple_brackets_fail_extra_closed() {
        let r = balanced_brackets("(()))");
        let expected = "closing bracket ) with no opening bracket at char 5";
        assert_that!(r).is_err();
        assert_that!(r.err().unwrap()).is_equal_to(expected.to_string())
    }

    #[test]
    fn test_simple_brackets_fail_mismatched() {
        let r = balanced_brackets("((])");
        let expected = "closing bracket ] at char 3 mismatched with last opening bracket (";
        assert_that!(r).is_err();
        assert_that!(r.err().unwrap()).is_equal_to(expected.to_string())
    }

    #[test]
    fn test_complex_text_success() {
        let r = balanced_brackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).");
        assert_that!(r).is_ok()
    }

    #[test]
    fn test_complex_mixed_text_success() {
        let r = balanced_brackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget [to {nest}].).");
        assert_that!(r).is_ok()
    }

    #[test]
    fn test_complex_text_mismatched() {
        let r = balanced_brackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).");
        let expected = "closing bracket ] at char 36 mismatched with last opening bracket (";
        assert_that!(r).is_err();
        assert_that!(r.err().unwrap()).is_equal_to(expected.to_string())
    }

    #[test]
    fn test_foo() {
        let r = foo();
        assert_that!(r).is_some();
        let s = r.unwrap();
        assert_that!(s.id).is_equal_to(1);
        assert_that!(s.name).is_equal_to("foo".to_string());
        assert_that!(s.labels).contains_exactly_in_order(vec!{"a".to_string(), "b".to_string()});
    }


}
