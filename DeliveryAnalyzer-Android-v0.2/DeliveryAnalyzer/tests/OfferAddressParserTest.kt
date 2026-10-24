import com.deliveryanalyzer.app.OfferAddressParser

fun main() {
    fun t(text: String, y: Int) = OfferAddressParser.ScreenText(text, y, 10)
    val screenshot = listOf(
        t("+ 9,65 €", 100), t("+ 38,8 km", 140),
        t("CHIKIN BANG - Korean Street Food Rennes", 200),
        t("4 Rue du Chêne Germain, 35510, Cesson-", 230),
        t("Sévigné", 260), t("Accepter", 330)
    )
    check(OfferAddressParser.detect(screenshot).address == "4 Rue du Chêne Germain, 35510, Cesson-Sévigné")
    val twoAddresses = listOf(t("22 rue du Marché, 35000 Rennes", 110),t("Restaurant ABC", 140),t("1 rue de la Clairière, 95280 Jouy-le-Moutier", 230),t("Accepter", 330))
    check(OfferAddressParser.detect(twoAddresses).address == "1 rue de la Clairière, 95280 Jouy-le-Moutier")
    check(OfferAddressParser.detect(listOf(t("22 rue du Marché, 35000 Rennes", 100))).address == null)
    println("3 tests OK: adresse sur deux lignes, deux adresses, absence de bouton")
}
