package com.yourorg;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MakeFalseInstanceMethodsStaticTest implements RewriteTest {

    //Note, you can define defaults for the RecipeSpec and these defaults will be used for all tests.
    //In this case, the recipe and the parser are common. See below, on how the defaults can be overridden
    //per test.
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MakeFalseInstanceMethodsStatic()).expectedCyclesThatMakeChanges(2);
    }

    @Test
    void addsStaticKeywordToFalseInstanceMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private void doSomethingStaticky() {
                            System.out.println("unchanging string");
                          }
                        }
                    """,
                """
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private static void doSomethingStaticky() {
                            System.out.println("unchanging string");
                          }
                        }
                    """
            )
        );
    }

    @Test
    void doesntModifyTrueInstanceMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private String magicWord;
                        
                          private String getMagicWord() {
                            return magicWord;
                          }
                        
                          private void setMagicWord(String value) {
                            magicWord = value;
                          }
                        
                        }
                    """,
                """
                        class Test {
                          private String magicWord;
                        
                          private String getMagicWord() {
                            return magicWord;
                          }
                        
                          private void setMagicWord(String value) {
                            magicWord = value;
                          }
                        
                        }
                    """
            )
        );
    }

    @Test
    void worksOnPrivateAndFinalMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private void arguablyStaticMethod1() {

                          }
                          
                          final void arguablyStaticMethod2() {
                          
                          }
                          
                          public final void arguablyStaticMethod3() {
                          
                          }
                          
                          protected final void arguablyStaticMethod4() {
                          
                          }
                          
                          private final void arguablyStaticMethod5() {
                          
                          }
                        }
                    """,
                """
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private static void arguablyStaticMethod1() {

                          }
                          
                          final static void arguablyStaticMethod2() {
                          
                          }
                          
                          public final static void arguablyStaticMethod3() {
                          
                          }
                          
                          protected final static void arguablyStaticMethod4() {
                          
                          }
                          
                          private final static void arguablyStaticMethod5() {
                          
                          }
                        }
                    """
            )
        );
    }

    @Test
    void doesntWorkOnNonFinalNonPrivateMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          void method1() {
                          
                          }
                          
                          public void method2() {
                          
                          }
                          
                          protected void method3() {
                          
                          }
                        }
                    """,
                """
                        class Test {
                          private String instanceWord = "sdlkfj";
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          void method1() {
                          
                          }
                          
                          public void method2() {
                          
                          }
                          
                          protected void method3() {
                          
                          }
                        }
                    """
            )
        );
    }

    // this one's not passing yet, checking why
    @Test
    void doesntModifyMethodsThatCallInstanceMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private String instanceWord;
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private String setInstanceWord(String newWord) {
                            instanceWord = newWord;
                          }
                          
                          private String getPhrase() {
                            return "something else"+getInstanceWord();
                          }
                          
                          private void printPhrase() {
                            System.out.println(getPhrase());
                          }
                          
                          private void printBlock(String firstLine) {
                            System.out.println(firstLine);
                            printPhrase();
                          }
                        }
                    """,
                """
                        class Test {
                          private String instanceWord;
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private String setInstanceWord(String newWord) {
                            instanceWord = newWord;
                          }
                          
                          private String getPhrase() {
                            return "something else"+getInstanceWord();
                          }
                          
                          private void printPhrase() {
                            System.out.println(getPhrase());
                          }
                          
                          private void printBlock(String firstLine) {
                            System.out.println(firstLine);
                            printPhrase();
                          }
                        }
                    """
            )
        );
    }

    @Test
    void doesntModifyOverridableMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private static String staticWord;
                        
                          protected String getStaticWord() {
                            return staticWord;
                          }
                        
                          public void setStaticWord(String value) {
                            staticWord = value;
                          }
                        }
                    """,
                """
                        class Test {
                          private static String staticWord;
                        
                          protected String getStaticWord() {
                            return staticWord;
                          }
                        
                          public void setStaticWord(String value) {
                            staticWord = value;
                          }
                        }
                    """
            )
        );
    }

    @Test
    void doesntModifyExcludedMethods() {
        rewriteRun(
            java("""
                        import java.io.Serializable;
                        import java.io.*;
                        
                        class Test implements Serializable {
                           private void writeObject(ObjectOutputStream stream) throws IOException {
                               
                           }
                       
                           private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
                               
                           }
                           
                           private void readObjectNoData() throws ObjectStreamException {
                           
                           }
                        }
                    """,
                """
                        import java.io.Serializable;
                        import java.io.*;
                        
                        class Test implements Serializable {
                           private void writeObject(ObjectOutputStream stream) throws IOException {
                               
                           }
                       
                           private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
                               
                           }
                           
                           private void readObjectNoData() throws ObjectStreamException {
                           
                           }
                        }
                    """
            )
        );


    }

    @Test
    void doesntGetConfusedByMultipleClassDeclarationsInOneFile() {
        rewriteRun(
            java("""
                        class A {
                            String word1 = "A instance word";
                            static String word2 = "A static word";
                            
                            private String getWord1() {
                                return word1;
                            }
                            
                            private String getWord2() {
                                return word2;
                            }
                        }
                        
                        class B {
                            // notice how word1 is static in class B but instance in class A
                            // and vice versa for word2
                            static String word1 = "B static word";
                            String word2 = "B instance word";
                            
                            private String getWord1() {
                                return word1;
                            }
                            
                            private String getWord2() {
                                return word2;
                            }
                        }
                    """,
                """
                        class A {
                            String word1 = "A instance word";
                            static String word2 = "A static word";
                            
                            private String getWord1() {
                                return word1;
                            }
                            
                            private static String getWord2() {
                                return word2;
                            }
                        }
                        
                        class B {
                            // notice how word1 is static in class B but instance in class A
                            // and vice versa for word2
                            static String word1 = "B static word";
                            String word2 = "B instance word";
                            
                            private static String getWord1() {
                                return word1;
                            }
                            
                            private String getWord2() {
                                return word2;
                            }
                        }
                    """
            )
        );


    }

// given more time would also be good to simulate an inherited class with methods that access instance data of the parent

// another edge case that i wasn't able to address yet vv
//    @Test
//    void checksNestedClassesAndMakesChangesIfAppropriate() {
//        rewriteRun(
//            java("""
//                        class Test {
//                          private static String magicWord = "magic";
//
//                          private String getMagicWord() {
//                            return magicWord;
//                          }
//
//                          private void setMagicWord(String value) {
//                            magicWord = value;
//                          }
//
//                          class NestedTest {
//                              private static String boringWord = "boring";
//
//                              private String getBoringWord() {
//                                return boringWord;
//                              }
//
//                              private void setBoringWord(String value) {
//                                boringWord = value;
//                              }
//                          }
//
//                        }
//                    """,
//                """
//                        class Test {
//                          private static String magicWord = "magic";
//
//                          private static String getMagicWord() {
//                            return magicWord;
//                          }
//
//                          private static void setMagicWord(String value) {
//                            magicWord = value;
//                          }
//
//                          class NestedTest {
//                              private static String boringWord = "boring";
//
//                              private static String getBoringWord() {
//                                return boringWord;
//                              }
//
//                              private static void setBoringWord(String value) {
//                                boringWord = value;
//                              }
//                          }
//                        }
//                    """
//            )
//        );
//    }
}
