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
        spec.recipe(new MakeFalseInstanceMethodsStatic());
    }

    @Test
    void demoTest() {
        rewriteRun(
            java("""
                        class Test {
                          private static String magicWord = "magic";
                          private String otherWord = "other";
                        
                          private String getMagicWord() {
                            String fancyFooBar = "fantastic" + otherWord;
                            otherWord = "somethingElse";
                            return magicWord;
                          }
                          
                          private String someOtherMethod() {
                            otherWord = "sldfkj";
                          }
                          
                          private static String midFileString = "sldkfj";
                        
                          private void setMagicWord(String value) {
                            magicWord = value;
                          }
                          
                          class Nested {
                              private static String magicWord2 = "magic2";
                              private String nestedOtherWord = "sldkfj";
                        
                              private String getMagicWord2() {
                                String fancyFooBar = "fantastic";
                                return magicWord2;
                              }
                              
                              private static String midFileString2 = "sldkfj2";
                            
                              private void setMagicWord2(String value) {
                                magicWord2 = value;
                              }
                          }
                        
                        }
                    """,
                """
                        class Test {
                          private static String magicWord = "magic";
                        
                          private static String getMagicWord() {
                            return magicWord;
                          }
                        
                          private static void setMagicWord(String value) {
                            magicWord = value;
                          }
                        
                        }
                    """
            )
        );
    }

    @Test
    void simple() {
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
    void addStaticKeywordToFauxInstanceMethods() {
        rewriteRun(
            java("""
                        class Test {
                          private static String magicWord = "magic";
                        
                          private String getMagicWord() {
                            String fancyFooBar = "fantastic";
                            return magicWord;
                          }
                          
                          // this person is weird and mean and declared a variable in the middle of their file :(
                          private static String midFileString = "sldkfj";
                        
                          private void setMagicWord(String value) {
                            magicWord = value;
                          }
                        
                        }
                    """,
                """
                        class Test {
                          private static String magicWord = "magic";
                        
                          private static String getMagicWord() {
                            return magicWord;
                          }
                        
                          private static void setMagicWord(String value) {
                            magicWord = value;
                          }
                        
                        }
                    """
            )
        );
    }

    @Test
    void checksNestedClassesAndMakesChangesIfAppropriate() {
        rewriteRun(
            java("""
                        class Test {
                          private static String magicWord = "magic";
                        
                          private String getMagicWord() {
                            return magicWord;
                          }
                        
                          private void setMagicWord(String value) {
                            magicWord = value;
                          }
                          
                          class NestedTest {
                              private static String boringWord = "boring";
                            
                              private String getBoringWord() {
                                return boringWord;
                              }
                            
                              private void setBoringWord(String value) {
                                boringWord = value;
                              }
                          }
                        
                        }
                    """,
                """
                        class Test {
                          private static String magicWord = "magic";
                        
                          private static String getMagicWord() {
                            return magicWord;
                          }
                        
                          private static void setMagicWord(String value) {
                            magicWord = value;
                          }
                          
                          class NestedTest {
                              private static String boringWord = "boring";
                            
                              private static String getBoringWord() {
                                return boringWord;
                              }
                            
                              private static void setBoringWord(String value) {
                                boringWord = value;
                              }
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
    void doesntModifyIfMethodCallsInstanceMethod() {
        rewriteRun(
            java("""
                        class Test {
                          private static String staticWord;
                          private String instanceWord;
                        
                          private String getStaticWord() {
                            return staticWord;
                          }
                          
                          private void setStaticWord(String value) {
                            staticWord = value;
                          }
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private void setInstanceWord(String value) {
                            instanceWord = value;
                          }
                          
                          private String getPhrase() {
                            return getStaticWord()+getInstanceWord();
                          }
                        
                        }
                    """,
                """
                        class Test {
                          private static String staticWord;
                          private String instanceWord;
                        
                          private static String getStaticWord() {
                            return staticWord;
                          }
                          
                          private static void setStaticWord(String value) {
                            staticWord = value;
                          }
                        
                          private String getInstanceWord() {
                            return instanceWord;
                          }
                          
                          private void setInstanceWord(String value) {
                            instanceWord = value;
                          }
                          
                          private String getPhrase() {
                            return getStaticWord()+getInstanceWord();
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
                          private String magicWord;
                          private String nonMagicWord;
                        
                          protected String getMagicWord() {
                            return magicWord;
                          }
                          
                          String getNonMagicWord() {
                            return nonMagicWord;
                          }
                        
                          public void setMagicWord(String value) {
                            magicWord = value;
                          }
                                                  
                        }
                    """,
                """
                        class Test {
                          private String magicWord;
                          private String nonMagicWord;
                        
                          protected String getMagicWord() {
                            return magicWord;
                          }
                          
                          String getNonMagicWord() {
                            return nonMagicWord;
                          }
                        
                          public void setMagicWord(String value) {
                            magicWord = value;
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
                        class Test {
                           private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
                               
                           }
                       
                           private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
                               
                           }
                           
                           private void readObjectNoData() throws ObjectStreamException {
                           
                           }
                        }
                    """,
                """
                        class Test {
                           private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
                               
                           }
                       
                           private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
                               
                           }
                           
                           private void readObjectNoData() throws ObjectStreamException {
                           
                           }
                        }
                    """
            )
        );
    }
}
