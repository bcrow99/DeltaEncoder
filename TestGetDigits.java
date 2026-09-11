import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Interactive demo: type two integers a and b, and this shows the
 * decimal digits of a/b's fractional part (static + repeating blocks),
 * then reconstructs a rational number from those digits and confirms it
 * matches (a % b) / b -- since getDecimalDigits only ever encodes the
 * fractional part (magnitude only, sign and integer part dropped), that
 * reduced remainder/b is the fair comparison, not the raw a/b typed in.
 */
public class TestGetDigits
{
	public static void main(String[] args)
	{
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter integer a: ");
		int a = Integer.parseInt(scanner.nextLine().trim());

		System.out.print("Enter integer b: ");
		int b = Integer.parseInt(scanner.nextLine().trim());

		ArrayList<String> digits = FractionMapper.getDecimalDigits(a, b);
		String staticDigits = digits.get(0);
		String repeatingDigits = digits.get(1);

		System.out.println();
		System.out.println("Static digits:    \"" + staticDigits + "\"" + (staticDigits.isEmpty() ? "  (none)" : ""));
		System.out.println("Repeating digits: \"" + repeatingDigits + "\"" + (repeatingDigits.isEmpty() ? "  (none -- terminates)" : ""));

		FractionMapper.BigFraction reconstructed = FractionMapper.getRationalNumber(staticDigits, repeatingDigits);
		System.out.println("\nReconstructed fraction: " + reconstructed);

		int remainder = Math.abs(a) % Math.abs(b);
		FractionMapper.BigFraction expected = new FractionMapper.BigFraction(
			BigInteger.valueOf(remainder), BigInteger.valueOf(Math.abs(b)));

		System.out.println("Expected (|a| % |b|) / |b|: " + expected);
		System.out.println("Got the integers back: " + reconstructed.equals(expected));

		scanner.close();
	}
}
