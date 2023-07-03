(function () {
    'use strict';

    angular.module('JahiaOAuthApp').controller('KeycloakController', KeycloakController);
    KeycloakController.$inject = ['$location', 'settingsService', 'helperService', 'i18nService'];

    function KeycloakController($location, settingsService, helperService, i18nService) {
        // must mach value in the plugin in pom.xml
        i18nService.addKey(jcauthkeycloaki18n);

        const CONNECTOR_SERVICE_NAME = 'KeycloakApi';

        const vm = this;

        vm.saveSettings = () => {
            // Value can't be empty
            if (!vm.apiKey || !vm.baseUrl || !vm.realm || !vm.callbackUrl || vm.callbackUrl.trim() === '') {
                helperService.errorToast(i18nService.message('label.missingMandatoryProperties'));
                return false;
            }

            // the node name here must be the same as the one in your spring file
            settingsService.setConnectorData({
                connectorServiceName: CONNECTOR_SERVICE_NAME,
                properties: {
                    enabled: vm.enabled,
                    apiKey: vm.apiKey,
                    apiSecret: 'DEFAULT_SECRET_UNUSED',
                    baseUrl: vm.baseUrl,
                    realm: vm.realm,
                    callbackUrl: vm.callbackUrl,
                    returnUrl: vm.returnUrl,
                    scope: vm.scope,
                    logoutSSO: vm.logoutSSO
                }
            }).success(() => {
                vm.connectorHasSettings = true;
                helperService.successToast(i18nService.message('label.saveSuccess'));
            }).error(data => helperService.errorToast(`${i18nService.message('jcauthnt_keycloakOAuthView')}: ${data.error}`));
        };

        vm.goToMappers = () => $location.path(`/mappers/${CONNECTOR_SERVICE_NAME}`);

        vm.toggleCard = () => vm.expandedCard = !vm.expandedCard;

        vm.init = () => {
            settingsService.getConnectorData(CONNECTOR_SERVICE_NAME, ['enabled', 'apiKey', 'baseUrl', 'realm', 'callbackUrl', 'returnUrl', 'scope', 'logoutSSO'])
                .success(data => {
                    if (data && !angular.equals(data, {})) {
                        vm.expandedCard = vm.connectorHasSettings = true;
                        vm.enabled = data.enabled;
                        vm.apiKey = data.apiKey;
                        vm.baseUrl = data.baseUrl;
                        vm.realm = data.realm;
                        vm.callbackUrl = data.callbackUrl || '';
                        vm.returnUrl = data.returnUrl || '';
                        vm.scope = data.scope;
                        vm.logoutSSO = data.logoutSSO === 'true';
                    } else {
                        vm.connectorHasSettings = false;
                        vm.enabled = false;
                    }
                })
                .error(data => helperService.errorToast(`${i18nService.message('jcauthnt_keycloakOAuthView')}: ${data.error}`));
        };
    }
})();
