import java.util.Scanner;

/**
 * Calculator Demo Class
 * 
 * This class demonstrates how to use the Calculator class
 * and provides a simple text-based interface for user interaction.
 */
public class CalculatorDemo {
    
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== Simple Calculator ===");
        System.out.println("Available operations:");
        System.out.println("1. Add (+)");
        System.out.println("2. Subtract (-)");
        System.out.println("3. Multiply (*)");
        System.out.println("4. Divide (/)");
        System.out.println("5. Modulo (%)");
        System.out.println("6. Reset (r)");
        System.out.println("7. Close (q)");
        System.out.println("8. Display current value (d)");
        
        while (calc.isRunning()) {
            System.out.print("\nEnter operation (or 'h' for help): ");
            String input = scanner.nextLine().trim().toLowerCase();
            
            try {
                switch (input) {
                    case "+":
                    case "1":
                        System.out.print("Enter number to add: ");
                        int addValue = Integer.parseInt(scanner.nextLine());
                        System.out.println("Result: " + calc.add(addValue));
                        break;
                        
                    case "-":
                    case "2":
                        System.out.print("Enter number to subtract: ");
                        int subValue = Integer.parseInt(scanner.nextLine());
                        System.out.println("Result: " + calc.subtract(subValue));
                        break;
                        
                    case "*":
                    case "3":
                        System.out.print("Enter number to multiply: ");
                        int mulValue = Integer.parseInt(scanner.nextLine());
                        System.out.println("Result: " + calc.multiply(mulValue));
                        break;
                        
                    case "/":
                    case "4":
                        System.out.print("Enter number to divide by: ");
                        int divValue = Integer.parseInt(scanner.nextLine());
                        System.out.println("Result: " + calc.divide(divValue));
                        break;
                        
                    case "%":
                    case "5":
                        System.out.print("Enter number for modulo: ");
                        int modValue = Integer.parseInt(scanner.nextLine());
                        System.out.println("Result: " + calc.modulo(modValue));
                        break;
                        
                    case "r":
                    case "6":
                        calc.reset();
                        break;
                        
                    case "q":
                    case "7":
                        calc.close();
                        break;
                        
                    case "d":
                    case "8":
                        calc.display();
                        break;
                        
                    case "h":
                        printHelp();
                        break;
                        
                    default:
                        System.out.println("Invalid operation. Type 'h' for help.");
                }
                
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid integer.");
            } catch (ArithmeticException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        
        scanner.close();
        System.out.println("Thank you for using the calculator!");
    }
    
    /**
     * Print help information
     */
    private static void printHelp() {
        System.out.println("\n=== Calculator Help ===");
        System.out.println("Operations:");
        System.out.println("  + or 1: Addition");
        System.out.println("  - or 2: Subtraction");
        System.out.println("  * or 3: Multiplication");
        System.out.println("  / or 4: Division");
        System.out.println("  % or 5: Modulo");
        System.out.println("  r or 6: Reset calculator to 0");
        System.out.println("  q or 7: Quit calculator");
        System.out.println("  d or 8: Display current value");
        System.out.println("  h: Show this help");
    }
}