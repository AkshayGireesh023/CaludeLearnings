package com.kharcha.app

import com.kharcha.app.data.Category
import com.kharcha.app.data.TxnType
import com.kharcha.app.parser.Categorizer
import com.kharcha.app.parser.NotificationParser
import com.kharcha.app.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    private val ts = 1_730_800_000_000L

    private fun p(sender: String, body: String) = SmsParser.parse(sender, body, ts)

    @Test fun hdfcUpiDebit() {
        val t = p("VM-HDFCBK", "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo swiggy@icici\nOn 05/11/24\nRef 431012345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(500.0, t.amount, 0.001)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("Swiggy", t.merchant)
        assertEquals("1234", t.account)
        assertEquals("HDFC Bank", t.bank)
        assertEquals("431012345678", t.refNo)
        assertEquals(Category.FOOD, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun sbiDebitWithoutCurrencySymbol() {
        val t = p("AD-SBIUPI-S", "Dear UPI user A/C X4321 debited by 250.0 on date 05Nov24 trf to ZOMATO Refno 431012345679. If not u? call 1800111109. -SBI")!!
        assertEquals(250.0, t.amount, 0.001)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("Zomato", t.merchant)
        assertEquals("4321", t.account)
        assertEquals("SBI", t.bank)
    }

    @Test fun iciciDebit() {
        val t = p("JD-ICICIB", "ICICI Bank Acct XX123 debited for Rs 1,250.00 on 05-Nov-24; BLINKIT credited. UPI:431012345670. Call 18002662 for dispute.")!!
        assertEquals(1250.0, t.amount, 0.001)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("Blinkit", t.merchant)
        assertEquals(Category.GROCERIES, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun axisUpiPath() {
        val t = p("VM-AXISBK", "INR 199.00 debited A/c no. XX9876 05-11-24, 10:15:12 UPI/P2M/431012345671/NETFLIX Not you? SMS BLOCKUPI Cust ID to 919951860002")!!
        assertEquals(199.0, t.amount, 0.001)
        assertEquals("Netflix", t.merchant)
        assertEquals("9876", t.account)
        assertEquals("431012345671", t.refNo)
        assertEquals(Category.ENTERTAINMENT, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun salaryCreditWithBalance() {
        val t = p("VM-HDFCBK", "Your A/c XX1234 is credited with INR 50,000.00 on 01-11-24 by NEFT-ACME CORP. Avl Bal INR 75,250.50")!!
        assertEquals(50000.0, t.amount, 0.001)
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals(75250.50, t.balance!!, 0.001)
        assertEquals("Acme Corp", t.merchant)
        assertEquals(Category.INCOME, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun upiCreditFromPerson() {
        val t = p("VM-HDFCBK", "Rs.2000.00 credited to HDFC Bank A/c XX1234 on 05-11-24 from VPA rahul.k@okaxis (UPI 431012345672)")!!
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals("Rahul K", t.merchant)
    }

    @Test fun debitBalanceIsNotTheAmount() {
        val t = p("BX-KOTAKB", "Rs 340 debited from a/c XX5555 to UBER INDIA on 05-11-24. Avl bal: Rs 12,345.00")!!
        assertEquals(340.0, t.amount, 0.001)
        assertEquals(12345.0, t.balance!!, 0.001)
        assertEquals(Category.TRANSPORT, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun creditCardSpendHasNoBalance() {
        val t = p("VM-HDFCBK", "Rs 1,299.00 spent on HDFC Bank Card x1234 at AMAZON on 2024-11-05:10:15:00. Avl Lmt: Rs 50,000")!!
        assertEquals(1299.0, t.amount, 0.001)
        assertEquals("Amazon", t.merchant)
        assertNull(t.balance)
        assertEquals(Category.SHOPPING, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun personToPersonIsTransfer() {
        val t = p("VM-HDFCBK", "Sent Rs.800.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 05/11/24 Ref 431012345673")!!
        assertEquals("UPI ••3210", t.merchant)
        assertEquals(Category.TRANSFER, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun atmIsCash() {
        val t = p("VM-SBIINB", "Rs 2000 withdrawn at ATM S1AW000123 from A/c X4321 on 05Nov24. Avl Bal Rs 8000")!!
        assertEquals(Category.CASH, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun refundOfFailedTxnIsKept() {
        val t = p("VM-HDFCBK", "Rs 450.00 credited to your A/c XX1234 towards reversal of failed UPI txn Ref 431012345674")!!
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals(Category.REFUND, Categorizer.categorize(t.merchant, t.raw, t.type, t.isPersonVpa))
    }

    @Test fun footerMentioningOtpDoesNotReject() {
        assertNotNull(p("VM-ICICIB", "Rs 120.00 debited from Acct XX123 to CHAAYOS on 05-Nov. Never share OTP/PIN with anyone."))
    }

    @Test fun rejectsNonTransactions() {
        assertNull(p("VM-HDFCBK", "123456 is your OTP for txn of Rs 500 at Amazon. Do not share."))
        assertNull(p("VM-HDFCBK", "Rahul has requested money of Rs 500 from you on PhonePe."))
        assertNull(p("VM-HDFCBK", "Your credit card bill of Rs 5,400 is due on 15-Nov."))
        assertNull(p("AD-HDFCBK", "Congratulations! You are eligible for a pre-approved loan of Rs 5,00,000. Apply now."))
        assertNull(p("VM-HDFCBK", "UPI txn of Rs 300 to Swiggy failed. Amount not debited."))
        assertNull(p("+919876543210", "Sent Rs 500 to you"))
    }

    @Test fun phonePeNotifications() {
        val d = NotificationParser.parse("com.phonepe.app", "Paid ₹250 to Swiggy", "Payment successful", ts)!!
        assertEquals(TxnType.DEBIT, d.type)
        assertEquals(250.0, d.amount, 0.001)
        assertEquals("Swiggy", d.merchant)

        val c = NotificationParser.parse("com.phonepe.app", "₹1,000 received", "Received from Priya Sharma", ts)!!
        assertEquals(TxnType.CREDIT, c.type)
        assertEquals("Priya Sharma", c.merchant)

        assertNull(NotificationParser.parse("com.phonepe.app", "You won ₹10 cashback", "Scratch card", ts))
        assertNull(NotificationParser.parse("com.whatsapp", "Paid ₹250 to Swiggy", null, ts))
    }

    @Test fun categorizerUsesWordBoundaries() {
        assertEquals(Category.ENTERTAINMENT, Categorizer.keywordCategory("Amazon Prime"))
        assertEquals(Category.GROCERIES, Categorizer.keywordCategory("Swiggy Instamart"))
        assertNull(Categorizer.keywordCategory("Motorola"))
        assertEquals(Category.TRANSPORT, Categorizer.keywordCategory("OLA cabs"))
    }
}
