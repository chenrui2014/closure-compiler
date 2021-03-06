/*
 * Copyright 2008 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author johnlenz@google.com (John Lenz)
 *
 */
public final class NormalizeTest extends Es6CompilerTestCase {

  private static final String EXTERNS = "var window; var Arguments;";

  public NormalizeTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Normalize(compiler, false);
  }

  @Override
  protected int getNumRepetitions() {
    // The normalize pass is only run once.
    return 1;
  }

  public void testSplitVar() {
    testSame("var a");
    test("var a, b",
         "var a; var b");
    test("var a, b, c",
         "var a; var b; var c");
    testSame("var a = 0 ");
    test("var a = 0 , b = foo()",
         "var a = 0; var b = foo()");
    test("var a = 0, b = 1, c = 2",
         "var a = 0; var b = 1; var c = 2");
    test("var a = foo(1), b = foo(2), c = foo(3)",
         "var a = foo(1); var b = foo(2); var c = foo(3)");

    test("try{var b = foo(1), c = foo(2);} finally { foo(3) }",
         "try{var b = foo(1); var c = foo(2)} finally { foo(3); }");
    test("try{var b = foo(1),c = foo(2);} finally {}",
         "try{var b = foo(1); var c = foo(2)} finally {}");
    test("try{foo(0);} finally { var b = foo(1), c = foo(2); }",
         "try{foo(0);} finally {var b = foo(1); var c = foo(2)}");

    test("switch(a) {default: var b = foo(1), c = foo(2); break;}",
         "switch(a) {default: var b = foo(1); var c = foo(2); break;}");

    test("do var a = foo(1), b; while(false);",
         "do{var a = foo(1); var b} while(false);");
    test("a:var a,b,c;",
         "a:{ var a;var b; var c; }");
    test("if (true) a:var a,b;",
         "if (true)a:{ var a; var b; }");
  }

  public void testSplitVar_forLoop() {
    // Verify vars extracted from FOR nodes are split.
    test(
        "for(var a = 0, b = foo(1), c = 1; c < b; c++) foo(2)",
        "var a = 0; var b = foo(1); var c = 1; for(; c < b; c++) foo(2)");

    // Verify split vars properly introduce blocks when needed.
    test("for(;;) var b = foo(1), c = foo(2);", "for(;;){var b = foo(1); var c = foo(2)}");
    test("for(;;){var b = foo(1), c = foo(2);}", "for(;;){var b = foo(1); var c = foo(2)}");

    test("a:for(var a,b,c;;);", "var a;var b; var c;a:for(;;);");
  }

  public void testSplitLet() {
    testSameEs6("let a");
    testEs6("let a, b", "let a; let b");
    testEs6("let a, b, c", "let a; let b; let c");
    testSameEs6("let a = 0 ");
    testEs6("let a = 0 , b = foo()", "let a = 0; let b = foo()");
    testEs6("let a = 0, b = 1, c = 2", "let a = 0; let b = 1; let c = 2");
    testEs6(
        "let a = foo(1), b = foo(2), c = foo(3)", "let a = foo(1); let b = foo(2); let c = foo(3)");
    testSameEs6("for (let a = 0, b = 1;;) {}");
  }

  public void testLetManyBlocks() {
    testEs6(
        LINE_JOINER.join(
            "let a = 'outer';",
            "{ let a = 'inner1'; }",
            "{ let a = 'inner2'; }",
            "{ let a = 'inner3'; }",
            "{ let a = 'inner4'; }"),
        LINE_JOINER.join(
            "let a = 'outer';",
            "{ let a$jscomp$1 = 'inner1'; }",
            "{ let a$jscomp$2 = 'inner2'; }",
            "{ let a$jscomp$3 = 'inner3'; }",
            "{ let a$jscomp$4 = 'inner4'; }"));
  }

  public void testLetOutsideAndInsideForLoop() {
    testEs6(
        LINE_JOINER.join(
            "let a = 'outer';",
            "for (let a = 'inner';;) {",
            "  break;",
            "}",
            "alert(a);"),
        LINE_JOINER.join(
            "let a = 'outer';",
            "for (let a$jscomp$1 = 'inner';;) {",
            "  break;",
            "}",
            "alert(a);"));
  }

  public void testLetOutsideAndInsideBlock() {
    testEs6(
        LINE_JOINER.join(
            "let a = 'outer';",
            "{",
            "  let a = 'inner';",
            "}",
            "alert(a);"),
        LINE_JOINER.join(
            "let a = 'outer';",
            "{",
            "  let a$jscomp$1 = 'inner';",
            "}",
            "alert(a);"));
  }

  public void testLetOutsideAndInsideFn() {
    testEs6(
        LINE_JOINER.join(
            "let a = 'outer';",
            "function f() {",
            "  let a = 'inner';",
            "}",
            "alert(a);"),
        LINE_JOINER.join(
            "let a = 'outer';",
            "function f() {",
            "  let a$jscomp$1 = 'inner';",
            "}",
            "alert(a);"));
  }

  public void testClassInForLoop() {
    testSameEs6("for (class a {};;) { break; }");
  }

  public void testFunctionInForLoop() {
    testSameEs6("for (function a() {};;) { break; }");
  }

  public void testLetInGlobalHoistScope() {
    testSameEs6(
        LINE_JOINER.join(
            "if (true) {",
            "  let x = 1; alert(x);",
            "}"));

    testEs6(
        LINE_JOINER.join(
            "if (true) {",
            "  let x = 1; alert(x);",
            "} else {",
            "  let x = 1; alert(x);",
            "}"),
        LINE_JOINER.join(
            "if (true) {",
            "  let x = 1; alert(x);",
            "} else {",
            "  let x$jscomp$1 = 1; alert(x$jscomp$1);",
            "}"));
  }

  public void testConstInGlobalHoistScope() {
    testSameEs6(
        LINE_JOINER.join(
            "if (true) {",
            "  const x = 1; alert(x);",
            "}"));

    testEs6(
        LINE_JOINER.join(
            "if (true) {",
            "  const x = 1; alert(x);",
            "} else {",
            "  const x = 1; alert(x);",
            "}"),
        LINE_JOINER.join(
            "if (true) {",
            "  const x = 1; alert(x);",
            "} else {",
            "  const x$jscomp$1 = 1; alert(x$jscomp$1);",
            "}"));
  }

  public void testVarReferencedInHoistedFunction() {
    test(
        LINE_JOINER.join(
            "var f1 = function() {",
            "  var x;",
            "};",
            "",
            "(function () {",
            "  {",
            "    var x = 0;",
            "  }",
            "  function f2() {",
            "    alert(x);",
            "  }",
            "  f2();",
            "})();"),
        LINE_JOINER.join(
            "var f1 = function() {",
            "  var x;",
            "};",
            "",
            "(function () {",
            "  function f2() {",
            "    alert(x$jscomp$1);",
            "  }",
            "  {",
            "    var x$jscomp$1 = 0;",
            "  }",
            "  f2();",
            "})();"));
  }

  public void testAssignShorthand() {
    test("x |= 1;", "x = x | 1;");
    test("x ^= 1;", "x = x ^ 1;");
    test("x &= 1;", "x = x & 1;");
    test("x <<= 1;", "x = x << 1;");
    test("x >>= 1;", "x = x >> 1;");
    test("x >>>= 1;", "x = x >>> 1;");
    test("x += 1;", "x = x + 1;");
    test("x -= 1;", "x = x - 1;");
    test("x *= 1;", "x = x * 1;");
    test("x /= 1;", "x = x / 1;");
    test("x %= 1;", "x = x % 1;");

    test("/** @suppress {const} */ x += 1;", "/** @suppress {const} */ x = x + 1;");
  }

  public void testDuplicateVarInExterns() {
    test("var extern;",
         "/** @suppress {duplicate} */ var extern = 3;",
         "/** @suppress {duplicate} */ var extern = 3;",
         null, null);
  }

  public void testUnhandled() {
    testSame("var x = y = 1");
  }

  public void testFor() {
    // Verify assignments are extracted from the FOR init node.
    test("for(a = 0; a < 2 ; a++) foo();",
         "a = 0; for(; a < 2 ; a++) foo()");
    // Verify vars are extracted from the FOR init node.
    test("for(var a = 0; c < b ; c++) foo()",
         "var a = 0; for(; c < b ; c++) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for(var a = 0; c < b ; c++) foo()",
         "var a = 0; a:for(; c < b ; c++) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a:b:for(var a = 0; c < b ; c++) foo()",
         "var a = 0; a:b:for(; c < b ; c++) foo()");

    // Verify block are properly introduced for ifs.
    test("if(x) for(var a = 0; c < b ; c++) foo()",
         "if(x){var a = 0; for(; c < b ; c++) foo()}");

    // Any other expression.
    test("for(init(); a < 2 ; a++) foo();",
         "init(); for(; a < 2 ; a++) foo()");
  }

  public void testForIn1() {
    // Verify nothing happens with simple for-in
    testSame("for(a in b) foo();");

    // Verify vars are extracted from the FOR-IN node.
    test("for(var a in b) foo()",
         "var a; for(a in b) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for(var a in b) foo()",
         "var a; a:for(a in b) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a:b:for(var a in b) foo()",
         "var a; a:b:for(a in b) foo()");

    // Verify block are properly introduced for ifs.
    test("if (x) for(var a in b) foo()",
         "if (x) { var a; for(a in b) foo() }");
  }

  public void testForIn2() {
    setExpectParseWarningsThisTest();
    // Verify vars are extracted from the FOR-IN node.
    test("for(var a = foo() in b) foo()",
        "var a = foo(); for(a in b) foo()",
        LanguageMode.ECMASCRIPT5);
  }

  public void testWhile() {
    // Verify while loops are converted to FOR loops.
    test("while(c < b) foo()",
         "for(; c < b;) foo()");
  }

  public void testMoveFunctions1() {
    test("function f() { if (x) return; foo(); function foo() {} }",
         "function f() {function foo() {} if (x) return; foo(); }");
    test(
        LINE_JOINER.join(
            "function f() { ",
            "  function foo() {} ",
            "  if (x) return;",
            "  foo(); ",
            "  function bar() {} ",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  function foo() {}",
            "  function bar() {}",
            "  if (x) return;",
            "  foo();",
            "}"));
  }

  public void testMoveFunctions2() {
    testSame("function f() { function foo() {} }");
    test("function f() { f(); a:function bar() {} }",
         "function f() { f(); a:{ var bar = function () {} }}");
    test("function f() { f(); {function bar() {}}}",
         "function f() { f(); {var bar = function () {}}}");
    test("function f() { f(); if (true) {function bar() {}}}",
         "function f() { f(); if (true) {var bar = function () {}}}");
  }

  private static String inFunction(String code) {
    return "(function(){" + code + "})";
  }

  private void testSameInFunction(String code) {
    testSame(inFunction(code));
  }

  private void testInFunction(String code, String expected) {
    test(inFunction(code), inFunction(expected));
  }

  public void testNormalizeFunctionDeclarations() {
    testSame("function f() {}");
    testSame("var f = function () {}");
    test("var f = function f() {}",
         "var f = function f$jscomp$1() {}");
    testSame("var f = function g() {}");
    test("a:function g() {}",
         "a:{ var g = function () {} }");
    test("{function g() {}}",
         "{var g = function () {}}");
    testSame("if (function g() {}) {}");
    test("if (true) {function g() {}}",
         "if (true) {var g = function () {}}");
    test("if (true) {} else {function g() {}}",
         "if (true) {} else {var g = function () {}}");
    testSame("switch (function g() {}) {}");
    test("switch (1) { case 1: function g() {}}",
         "switch (1) { case 1: var g = function () {}}");
    test("if (true) {function g() {} function h() {}}",
         "if (true) {var h = function() {}; var g = function () {}}");


    testSameInFunction("function f() {}");
    testInFunction("f(); a:function g() {}",
                   "f(); a:{ var g = function () {} }");
    testInFunction("f(); {function g() {}}",
                   "f(); {var g = function () {}}");
    testInFunction("f(); if (true) {function g() {}}",
                   "f(); if (true) {var g = function () {}}");
    testInFunction("if (true) {} else {function g() {}}",
                   "if (true) {} else {var g = function () {}}");
  }

  public void testMakeLocalNamesUnique() {
    // Verify global names are untouched.
    testSame("var a;");

    // Verify global names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;function foo(a){var b;a}",
         "var a;function foo(a$jscomp$1){var b;a$jscomp$1}");
    test("var a;function foo(){var b;a}function boo(){var b;a}",
         "var a;function foo(){var b;a}function boo(){var b$jscomp$1;a}");
    test("function foo(a){var b} function boo(a){var b}",
         "function foo(a){var b} function boo(a$jscomp$1){var b$jscomp$1}");

    // Verify function expressions are renamed.
    test("var a = function foo(){foo()};var b = function foo(){foo()};",
         "var a = function foo(){foo()};var b = function foo$jscomp$1(){foo$jscomp$1()};");

    // Verify catch exceptions names are made unique
    testSame("try { } catch(e) {e;}");
    test("try { } catch(e) {e;}; try { } catch(e) {e;}",
         "try { } catch(e) {e;}; try { } catch(e$jscomp$1) {e$jscomp$1;}");
    test("try { } catch(e) {e; try { } catch(e) {e;}};",
         "try { } catch(e) {e; try { } catch(e$jscomp$1) {e$jscomp$1;} }; ");

    // Verify the 1st global redefinition of extern definition is not removed.
    testSame("/** @suppress {duplicate} */ var window;");

    // Verify the 2nd global redefinition of extern definition is removed.
    test("/** @suppress {duplicate} */ var window; /** @suppress {duplicate} */ var window;",
         "/** @suppress {duplicate} */ var window;");

    // Verify local masking extern made unique.
    test("function f() {var window}",
         "function f() {var window$jscomp$1}");
  }

  public void testMakeParamNamesUnique() {
    test(
        "function f(x) { x; }\nfunction g(x) { x; }",
        "function f(x) { x; }\nfunction g(x$jscomp$1) { x$jscomp$1; }");

    testEs6(
        "function f(x) { x; }\nfunction g(...x) { x; }",
        "function f(x) { x; }\nfunction g(...x$jscomp$1) { x$jscomp$1; }");

    testEs6(
        "function f(x) { x; }\nfunction g({x: x}) { x; }",
        "function f(x) { x; }\nfunction g({x: x$jscomp$1}) { x$jscomp$1; }");

    testEs6(
        "function f(x) { x; }\nfunction g({x}) { x; }",
        "function f(x) { x; }\nfunction g({x: x$jscomp$1}) { x$jscomp$1; }");

    testEs6(
        "function f(x) { x; }\nfunction g({y: {x}}) { x; }",
        "function f(x) { x; }\nfunction g({y: {x: x$jscomp$1}}) { x$jscomp$1; }");
  }

  public void testNoRenameParamNames() {
    testSame("function f(x) { x; }");

    testSameEs6("function f(...x) { x; }");

    testSameEs6("function f({x: x}) { x; }");

    testSameEs6("function f({x}) { x; }");

    testSameEs6("function f({y: {x}}) { x; }");
  }

  public void testRemoveDuplicateVarDeclarations1() {
    test("function f() { var a; var a }",
         "function f() { var a; }");
    test("function f() { var a = 1; var a = 2 }",
         "function f() { var a = 1; a = 2 }");
    test("var a = 1; function f(){ var a = 2 }",
         "var a = 1; function f(){ var a$jscomp$1 = 2 }");
    test(
        "function f() { var a = 1; label1:var a = 2 }",
        "function f() { var a = 1; label1:{a = 2}}");
    test("function f() { var a = 1; label1:var a }", "function f() { var a = 1; label1:{} }");
    test("function f() { var a = 1; for(var a in b); }",
         "function f() { var a = 1; for(a in b); }");
  }

  public void testRemoveDuplicateVarDeclarations2() {
    test("var e = 1; function f(){ try {} catch (e) {} var e = 2 }",
         "var e = 1; function f(){ try {} catch (e$jscomp$2) {} var e$jscomp$1 = 2 }");
  }

  public void testRemoveDuplicateVarDeclarations3() {
    test("var f = 1; function f(){}",
         "f = 1; function f(){}");
    test("var f; function f(){}",
         "function f(){}");
    test("if (a) { var f = 1; } else { function f(){} }",
         "if (a) { var f = 1; } else { f = function (){} }");

    test("function f(){} var f = 1;",
         "function f(){} f = 1;");
    test("function f(){} var f;",
         "function f(){}");
    test("if (a) { function f(){} } else { var f = 1; }",
         "if (a) { var f = function (){} } else { f = 1; }");

    // TODO(johnlenz): Do we need to handle this differently for "third_party"
    // mode? Remove the previous function definitions?
    testSame("function f(){} function f(){}");
    test("if (a) { function f(){} } else { function f(){} }",
         "if (a) { var f = function (){} } else { f = function (){} }");
  }

  // It's important that we not remove this var completely. See
  // http://blickly.github.io/closure-compiler-issues/#290
  public void testRemoveDuplicateVarDeclarations4() {
    testSame("if (!Arguments) { /** @suppress {duplicate} */ var Arguments = {}; }");
  }

  // If there are multiple duplicates, it's okay to remove all but the first.
  public void testRemoveDuplicateVarDeclarations5() {
    test("var Arguments = {}; var Arguments = {};", "var Arguments = {}; Arguments = {};");
  }

  public void testRenamingConstants() {
    testSame("var ACONST = 4; var b = ACONST;");

    test("var a, ACONST = 4;var b = ACONST;",
         "var a; var ACONST = 4; var b = ACONST;");

    testSame("var ACONST; ACONST = 4; var b = ACONST;");

    testSame("var ACONST = new Foo(); var b = ACONST;");

    testSame("/** @const */ var aa; aa = 1;");
  }

  public void testSkipRenamingExterns() {
    test("var EXTERN; var ext; ext.FOO;", "var b = EXTERN; var c = ext.FOO",
         "var b = EXTERN; var c = ext.FOO", null, null);
  }

  public void testIssue166e() {
    test("var e = 2; try { throw 1 } catch(e) {}",
         "var e = 2; try { throw 1 } catch(e$jscomp$1) {}");
  }

  public void testIssue166f() {
    test(
        LINE_JOINER.join(
            "function a() {",
            "  var e = 2;",
            "  try { throw 1 } catch(e) {}",
            "}"),
        LINE_JOINER.join(
             "function a() {",
             "  var e = 2;",
             "  try { throw 1 } catch(e$jscomp$1) {}",
             "}"));
  }

  public void testIssue166g() {
    test(
        LINE_JOINER.join(
            "function a() {",
            "  try { throw 1 } catch(e) {}",
            "  var e = 2;",
            "}"),
        LINE_JOINER.join(
             "function a() {",
             "  try { throw 1 } catch(e$jscomp$1) {}",
             "  var e = 2;",
             "}"));
  }

  public void testLetsInSeparateBlocks() {
    testEs6(
        LINE_JOINER.join(
            "if (x) {",
            "  let e;",
            "  alert(e);",
            "}",
            "if (y) {",
            "  let e;",
            "  alert(e);",
            "}"),
        LINE_JOINER.join(
            "if (x) {",
            "  let e;",
            "  alert(e);",
            "}",
            "if (y) {",
            "  let e$jscomp$1;",
            "  alert(e$jscomp$1);",
            "}"));
  }

  public void testCatchesInSeparateBlocks() {
    test(
        LINE_JOINER.join(
            "if (x) {",
            "  try {",
            "    throw 1;",
            "  } catch (e) {",
            "    alert(e);",
            "  }",
            "}",
            "if (y) {",
            "  try {",
            "    throw 2;",
            "  } catch (e) {",
            "    alert(e);",
            "  }",
            "}"),
        LINE_JOINER.join(
            "if (x) {",
            "  try {",
            "    throw 1;",
            "  } catch (e) {",
            "    alert(e);",
            "  }",
            "}",
            "if (y) {",
            "  try {",
            "    throw 2;",
            "  } catch (e$jscomp$1) {",
            "    alert(e$jscomp$1);",
            "  }",
            "}"));
  }

  public void testDeclInCatchBlock() {
    testEs6(
        LINE_JOINER.join(
            "var x;",
            "try {",
            "} catch (e) {",
            "  let x;",
            "}"),
        LINE_JOINER.join(
            "var x;",
            "try {",
            "} catch (e) {",
            "  let x$jscomp$1",
            "}"));
  }

  public void testIssue() {
    super.allowExternsChanges(true);
    test("var a,b,c; var a,b", "a(), b()", "a(), b()", null, null);
  }

  public void testNormalizeSyntheticCode() {
    Compiler compiler = new Compiler();
    compiler.init(
        new ArrayList<SourceFile>(),
         new ArrayList<SourceFile>(), new CompilerOptions());
    String code = "function f(x) {} function g(x) {}";
    Node ast = compiler.parseSyntheticCode(code);
    Normalize.normalizeSyntheticCode(compiler, ast, "prefix_");
    assertEquals(
        "function f(x$jscomp$prefix_0){}function g(x$jscomp$prefix_1){}",
        compiler.toSource(ast));
  }

  public void testIsConstant() {
    testSame("var CONST = 3; var b = CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, Node.IS_CONSTANT_NAME);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertEquals("CONST", hasProp.getString());
    }
  }

  public void testPropertyIsConstant1() {
    testSame("var a = {};a.CONST = 3; var b = a.CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, Node.IS_CONSTANT_NAME);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertEquals("CONST", hasProp.getString());
    }
  }

  public void testPropertyIsConstant2() {
    testSame("var a = {CONST: 3}; var b = a.CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, Node.IS_CONSTANT_NAME);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertEquals("CONST", hasProp.getString());
    }
  }

  public void testGetterPropertyIsConstant() {
    testSame("var a = { get CONST() {return 3} }; var b = a.CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, Node.IS_CONSTANT_NAME);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertEquals("CONST", hasProp.getString());
    }
  }

  public void testSetterPropertyIsConstant() {
    // Verifying that a SET is properly annotated.
    testSame("var a = { set CONST(b) {throw 'invalid'} }; var c = a.CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, Node.IS_CONSTANT_NAME);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertEquals("CONST", hasProp.getString());
    }
  }

  public void testExposeSimple() {
    test("var x = {}; /** @expose */ x.y = 3; x.y = 5;",
         "var x = {}; /** @expose */ x['y'] = 3; x['y'] = 5;");
  }

  public void testExposeComplex() {
    test("var x = {/** @expose */ a: 1, b: 2}; x.a = 3; /** @expose */ x.b = 5;",
         "var x = {/** @expose */ 'a': 1, 'b': 2}; x['a'] = 3; /** @expose */ x['b'] = 5;");
  }

  public void testShadowFunctionName() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  var f = 'test';",
            "  console.log(f);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var f$jscomp$1 = 'test';",
            "  console.log(f$jscomp$1);",
            "}"));
  }

  private Set<Node> findNodesWithProperty(Node root, final int prop) {
    final Set<Node> set = new HashSet<>();
    NodeTraversal.traverseEs6(
        getLastCompiler(), root, new AbstractPostOrderCallback() {
        @Override
        public void visit(NodeTraversal t, Node node, Node parent) {
          if (node.getBooleanProp(prop)) {
            set.add(node);
          }
        }
      });
    return set;
  }

  public void testRenamingConstantProperties() throws Exception {
    // In order to detect that foo.BAR is a constant, we need collapse
    // properties to run first so that we can tell if the initial value is
    // non-null and immutable. The Normalize pass doesn't modify the code
    // in these examples, it just infers const-ness of some variables, so
    // we call enableNormalize to make the Normalize.VerifyConstants pass run.

    // TODO(johnlenz): fix this so it is just another test case.
    WithCollapse testCase = new WithCollapse();
    testCase.testConstantProperties();
    testCase.tearDown();
  }

  public static class WithCollapse extends CompilerTestCase {
    WithCollapse() {
      enableNormalize();
    }

    private void testConstantProperties() {
      test("var a={}; a.ACONST = 4;var b = 1; b = a.ACONST;",
          "var a$ACONST = 4; var b = 1; b = a$ACONST;");

      test("var a={b:{}}; a.b.ACONST = 4;var b = 1; b = a.b.ACONST;",
          "var a$b$ACONST = 4;var b = 1; b = a$b$ACONST;");

      test("var a = {FOO: 1};var b = 1; b = a.FOO;",
          "var a$FOO = 1; var b = 1; b = a$FOO;");

      testSame("var EXTERN; var ext; ext.FOO;", "var b = EXTERN; var c = ext.FOO", null);

      test("var a={}; a.ACONST = 4; var b = 1; b = a.ACONST;",
          "var a$ACONST = 4; var b = 1; b = a$ACONST;");

      test("var a = {}; function foo() { var d = a.CONST; }; (function(){a.CONST=4})();",
          "var a$CONST;function foo(){var d = a$CONST;}; (function(){a$CONST = 4})();");

      test("var a = {}; a.ACONST = new Foo(); var b = 1; b = a.ACONST;",
          "var a$ACONST = new Foo(); var b = 1; b = a$ACONST;");
    }

    @Override
    protected int getNumRepetitions() {
      // The normalize pass is only run once.
      return 1;
    }

    @Override
    protected CompilerPass getProcessor(Compiler compiler) {
      return new CollapseProperties(compiler);
    }
  }
}
