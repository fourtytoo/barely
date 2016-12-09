package biz.webperipheral;
import junit.framework.TestCase;

// TODO: all

public class BarelyTest extends TestCase {

  public BarelyTest(String name) {
    super(name);
  }

  public void testFoo() throws Exception {
    assertTrue( Barely.isDataAttribute(null) );
  }
  
}
