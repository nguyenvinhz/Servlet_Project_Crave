<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:if test="${not empty deliveryError}"><p data-delivery-error role="alert"><c:out value="${deliveryError}"/></p></c:if>
<c:forEach var="point" items="${locations}">
<button type="button" class="button outline delivery-search-result" data-latitude="${point.latitude()}" data-longitude="${point.longitude()}" data-approximate="${point.approximate()}"><c:out value="${point.label()}"/></button>
</c:forEach>
<c:if test="${not empty location}"><span data-location-label><c:out value="${location.label()}"/></span></c:if>
<c:if test="${not empty quote}"><span data-quote data-deliverable="${quote.deliverable()}" data-fee="${quote.deliveryFee()}"><c:out value="${quote.message()}"/></span></c:if>
