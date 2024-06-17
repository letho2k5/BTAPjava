import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class StudentProcessor {

    static class Student {
        String id;
        String name;
        String address;
        String dateOfBirth;
        int ageYears;
        int ageMonths;
        int ageDays;
        String encodedAge; // Corrected variable name
        boolean isPrime; // Whether the sum of digits is a prime number
        int sum; // Sum of digits in date of birth

        Student(String id, String name, String address, String dateOfBirth) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.dateOfBirth = dateOfBirth;
        }
    }

    static BlockingQueue<Student> queue1 = new LinkedBlockingQueue<>();
    static BlockingQueue<Student> queue2 = new LinkedBlockingQueue<>();
    static BlockingQueue<Student> processedQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) throws Exception {
        // Delete output file before starting
        new File("kq.xml").delete();

        // Create threads and latch for synchronization
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);

        executor.execute(new Thread1(latch));
        executor.execute(new Thread2(latch));
        executor.execute(new Thread3(latch));

        // Shutdown executor after completion
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        // Wait for all threads to finish
        latch.await();

        // Write results to XML file
        writeResultsToXml();

        // Read and decode results from kq.xml
        readAndDecodeResults();
    }

    static class Thread1 implements Runnable {
        private final CountDownLatch latch;

        public Thread1(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                File file = new File("student.xml");
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(file);

                NodeList nList = doc.getElementsByTagName("student");

                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Element element = (Element) nList.item(temp);
                    String id = element.getElementsByTagName("id").item(0).getTextContent();
                    String name = element.getElementsByTagName("name").item(0).getTextContent();
                    String address = element.getElementsByTagName("address").item(0).getTextContent();
                    String dateOfBirth = element.getElementsByTagName("dateOfBirth").item(0).getTextContent();
                    Student student = new Student(id, name, address, dateOfBirth);
                    queue1.put(student);
                }
                // Signal end of data
                queue1.put(new Student("END", "", "", ""));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown(); // Decrease latch count
            }
        }
    }

    static class Thread2 implements Runnable {
        private final CountDownLatch latch;

        public Thread2(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Student student = queue1.take();
                    if (student.id.equals("END")) {
                        queue2.put(student); // Signal end to next thread
                        break;
                    }
                    if (student.dateOfBirth != null && !student.dateOfBirth.isEmpty()) {
                        String[] parts = student.dateOfBirth.split("-");
                        int birthYear = Integer.parseInt(parts[0]);
                        int birthMonth = Integer.parseInt(parts[1]);
                        int birthDay = Integer.parseInt(parts[2]);

                        // Calculate age in years, months, and days
                        int[] ageComponents = calculateAge(birthYear, birthMonth, birthDay);
                        student.ageYears = ageComponents[0];
                        student.ageMonths = ageComponents[1];
                        student.ageDays = ageComponents[2];

                        // Calculate sum of digits in date of birth
                        student.sum = calculateDigitSum(student.dateOfBirth);

                        // Encode sum of age components
                        student.encodedAge = encodeAge(student.ageYears);

                        queue2.put(student);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }

        private int[] calculateAge(int birthYear, int birthMonth, int birthDay) {
            // Current date assumption for calculating age
            int currentYear = 2024;
            int currentMonth = 6; // June
            int currentDay = 17;

            // Calculating age
            int ageYears = currentYear - birthYear;
            int ageMonths = currentMonth - birthMonth;
            int ageDays = currentDay - birthDay;

            // Adjusting negative months or days
            if (ageDays < 0) {
                ageMonths--;
                ageDays += 30; // Assuming 30 days in a month
            }
            if (ageMonths < 0) {
                ageYears--;
                ageMonths += 12; // 12 months in a year
            }

            return new int[] { ageYears, ageMonths, ageDays };
        }

        private int calculateDigitSum(String dateOfBirth) {
            int sum = 0;
            for (char c : dateOfBirth.toCharArray()) {
                if (Character.isDigit(c)) {
                    sum += Character.getNumericValue(c);
                }
            }
            return sum;
        }

        private String encodeAge(int years) {
            // Sum of age components and sum of digits
            int sum = years;

            // Encoding sum
            StringBuilder encoded = new StringBuilder();
            String sumStr = String.valueOf(sum);
            for (int i = 0; i < sumStr.length(); i++) {
                encoded.append((char) (sumStr.charAt(i) + 1)); // Increment each digit
            }
            return encoded.toString();
        }
    }

    static class Thread3 implements Runnable {
        private final CountDownLatch latch;

        public Thread3(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Student student = queue2.take();
                    if (student.id.equals("END")) {
                        processedQueue.put(student); // Signal end to processed queue
                        break;
                    }

                    // Check if the encoded sum is a prime number
                    student.isPrime = isPrime(student.sum);

                    processedQueue.put(student); // Put processed student into queue
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }

        private boolean isPrime(int number) {
            if (number <= 1) {
                return false;
            }
            if (number <= 3) {
                return true;
            }
            if (number % 2 == 0 || number % 3 == 0) {
                return false;
            }
            int i = 5;
            while (i * i <= number) {
                if (number % i == 0 || number % (i + 2) == 0) {
                    return false;
                }
                i += 6;
            }
            return true;
        }
    }

    static void writeResultsToXml() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElement = doc.createElement("students");
            doc.appendChild(rootElement);

            while (true) {
                Student student = processedQueue.take();
                if (student.id.equals("END")) {
                    break;
                }
                Element studentElement = doc.createElement("student");
                rootElement.appendChild(studentElement);

                Element id = doc.createElement("id");
                id.appendChild(doc.createTextNode(student.id));
                studentElement.appendChild(id);

                Element name = doc.createElement("name");
                name.appendChild(doc.createTextNode(student.name));
                studentElement.appendChild(name);

                Element address = doc.createElement("address");
                address.appendChild(doc.createTextNode(student.address));
                studentElement.appendChild(address);

                Element ageYears = doc.createElement("ageYears");
                ageYears.appendChild(doc.createTextNode(String.valueOf(student.ageYears)));
                studentElement.appendChild(ageYears);

                Element ageMonths = doc.createElement("ageMonths");
                ageMonths.appendChild(doc.createTextNode(String.valueOf(student.ageMonths)));
                studentElement.appendChild(ageMonths);

                Element ageDays = doc.createElement("ageDays");
                ageDays.appendChild(doc.createTextNode(String.valueOf(student.ageDays)));
                studentElement.appendChild(ageDays);

                Element encodedAge = doc.createElement("encodedAge");
                encodedAge.appendChild(doc.createTextNode(student.encodedAge));
                studentElement.appendChild(encodedAge);

                Element sum = doc.createElement("sum");
                sum.appendChild(doc.createTextNode(String.valueOf(student.sum)));
                studentElement.appendChild(sum);

                Element isPrime = doc.createElement("isPrime");
                isPrime.appendChild(doc.createTextNode(String.valueOf(student.isPrime)));
                studentElement.appendChild(isPrime);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File("kq.xml"));
            transformer.transform(source, result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void readAndDecodeResults() {
        try {
            File file = new File("kq.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);

            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName("student");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Element studentElement = (Element) nodeList.item(i);

                String id = studentElement.getElementsByTagName("id").item(0).getTextContent();
                String name = studentElement.getElementsByTagName("name").item(0).getTextContent();
                String address = studentElement.getElementsByTagName("address").item(0).getTextContent();
                String ageYears = studentElement.getElementsByTagName("ageYears").item(0).getTextContent();
                String ageMonths = studentElement.getElementsByTagName("ageMonths").item(0).getTextContent();
                String ageDays = studentElement.getElementsByTagName("ageDays").item(0).getTextContent();
                String encodedAge = studentElement.getElementsByTagName("encodedAge").item(0).getTextContent(); // Corrected variable name
                String isPrime = studentElement.getElementsByTagName("isPrime").item(0).getTextContent();
                String sum = studentElement.getElementsByTagName("sum").item(0).getTextContent();

                System.out.println("Student:");
                System.out.println("ID: " + id);
                System.out.println("Name: " + name);
                System.out.println("Address: " + address);
                System.out.println("Age (Years): " + ageYears);
                System.out.println("Age (Months): " + ageMonths);
                System.out.println("Age (Days): " + ageDays);
                System.out.println("Encoded Age: " + encodedAge);
                System.out.println("Sum of Digits: " + sum);
                System.out.println("Is Prime: " + isPrime);
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
            writeEmptyResult(); // Write empty result to file if there's an error
        }
    }

    static void writeEmptyResult() {
        try {
            FileWriter writer = new FileWriter("kq.xml");
            writer.write("<students>\n");
            writer.write("  <student>\n");
            writer.write("    <ageYears></ageYears>\n");
            writer.write("    <sum></sum>\n");
            writer.write("    <isPrime></isPrime>\n");
            writer.write("  </student>\n");
            writer.write("</students>\n");
            writer.close();
            System.out.println("Empty result file created: kq.xml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

