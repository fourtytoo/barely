package biz.webperipheral;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Person {
  private static final List<Person> staticPerson = new ArrayList<Person>() {
      {
	add (new Person("Jack Smith", "Jane Smith", false, 1));
	add (new Person("Jill Brown", "Paul Brown", true, 3));
	add (new Person("John Doe", "Judy Doe", true, 0));
	add (new Person("Bill Divorced", null, false, 1));
      }
    };

  private String name;
  private boolean married;
  private String spouse;
  private List<String> children;
	
  public Person () {
    this.name = "";
    this.married = false;
    this.spouse = "";
    children = Collections.emptyList();
  }

  public Person(String name, String spouse, boolean isMarried, int nChildren) {
    super();
    this.name = name;
    this.spouse = spouse;
    this.married = isMarried;
    children = new ArrayList<String>();
    for (int z=0;z<nChildren;z++) {
      children.add("Brat " + z);
    }
  }

  public static Person lookup (String id) {
    if (id!=null) {
      int _id = Integer.valueOf(id);
      // we should also check that id >=1 but we leave the bug so as
      // to see the error appear on the web page
      if (_id <= staticPerson.size()) {
	return staticPerson.get(_id-1);
      } else {
	return new Person ("Unknown Name", "Unknown spouse", false, 0);
      }
    } else {
      return new Person ("Unknown Name", "Unknown spouse", false, 0);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSpouse () {
    return this.spouse;
  }

  public void setSpouse(String spouse) {
    this.spouse = spouse;
  }

  public boolean isMarried() {
    return married;
  }

  public void setMarried(boolean married) {
    this.married = married;
  }

  public List<String> getChildren() {
    return children;
  }

  public void setChildren(List<String> children) {
    this.children = children;
  }

  @Override
  public String toString() {
    return "Person [name=" + name + ", married=" + married + ", spouse="
      + spouse + ", children=" + children + "]";
  }
}
