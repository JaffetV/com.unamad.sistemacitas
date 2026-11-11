/**
 * Loads the active marketplace screens after splitting the former monolithic
 * MarketplaceRemoteActivities.kt into one file per Activity and shared helpers.
 */
export async function loadRemoteActivities(read) {
  const paths = [
    'view/MarketplaceUiHelpers.kt',
    'view/AccesoRemotoActivity.kt',
    'view/RegistroNegocioRemotoActivity.kt',
    'view/MarketplaceRemotoActivity.kt',
    'view/PanelNegocioRemotoActivity.kt'
  ];
  const contents = await Promise.all(paths.map(read));
  return contents.join('\n\n');
}
